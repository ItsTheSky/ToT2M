// libtotmeditor.so : remplace le niveau joué par Tomb of the Mask 1.2.28 (arm64)
// par celui écrit par l'éditeur dans <filesDir>/test_level.bin.
//
// Cible : DataManager.StageInfo(int) (RVA 0x50E734), appelée uniquement par GameController.GenerateStage.
// libil2cpp.so est patchée statiquement (tools/patch_il2cpp.py) : StageInfo lit un pointeur dans
// un emplacement de données (SLOT, fin du .bss) et y saute s'il est non nul, sinon comportement d'origine.
// Ici on se contente d'écrire &hook_StageInfo dans SLOT : aucune page de code n'est modifiée.

#include <jni.h>
#include <dlfcn.h>
#include <pthread.h>
#include <unistd.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <stdarg.h>
#include <android/log.h>

#define TAG "TotMEditor"
#define LOGI(...) log_status(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOGE(...) log_status(ANDROID_LOG_ERROR, __VA_ARGS__)

static const uintptr_t RVA_STAGEINFO = 0x50E734;
static const uintptr_t RVA_SLOT = 0x1343A50;
static const uint32_t EXPECTED_PATCH[4] = {0xb00071b0, 0xf9452a10, 0xb4000050, 0xd61f0200};

// Disposition mémoire (dump IL2CPP)
static const size_t OFF_DATAMANAGER_STAGES = 0x20;  // List<StageInfo> stages
static const size_t OFF_LIST_ITEMS = 0x10;          // StageInfo[] _items
static const size_t OFF_LIST_SIZE = 0x18;           // int _size
static const size_t OFF_ARRAY_DATA = 0x20;          // début des éléments d'un tableau IL2CPP

struct StageInfo {      // 0x18 octets
    int32_t width;      // 0x0
    int32_t height;     // 0x4
    void *tiles;        // 0x8  byte[]
    uint8_t lavaSpeed;  // 0x10
};
static_assert(sizeof(StageInfo) == 0x18, "taille StageInfo");

typedef void *(*array_new_specific_t)(void *arrayClass, uintptr_t length);

static char g_filesDir[512];
static char g_status[512] = "lib chargée, en attente du lancement du jeu";
static array_new_specific_t g_array_new_specific = nullptr;
static volatile int g_hooked = 0;
static volatile int g_testServed = 0;

static void log_status(int prio, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(g_status, sizeof(g_status), fmt, ap);
    va_end(ap);
    __android_log_print(prio, TAG, "%s", g_status);
}

// ------------------------------------------------------------------ niveau de test
// Format identique à une entrée du TextAsset "stages" : [lava][w][h][w*h octets, lignes de bas en haut]
static bool load_test_level(uint8_t *lava, uint8_t *w, uint8_t *h, uint8_t *buf, size_t cap) {
    if (!g_filesDir[0]) return false;
    char path[600];
    snprintf(path, sizeof(path), "%s/test_level.bin", g_filesDir);
    FILE *f = fopen(path, "rb");
    if (!f) return false;
    uint8_t head[3];
    bool ok = fread(head, 1, 3, f) == 3;
    size_t n = ok ? (size_t)head[1] * head[2] : 0;
    ok = ok && n > 0 && n <= cap && fread(buf, 1, n, f) == n;
    fclose(f);
    if (!ok) { LOGE("test_level.bin invalide"); return false; }
    *lava = head[0]; *w = head[1]; *h = head[2];
    return true;
}

// Même convention d'appel que l'original : résultat > 16 octets renvoyé via x8.
extern "C" __attribute__((visibility("default")))
StageInfo hook_StageInfo(void *self, int32_t index, void * /*method*/) {
    StageInfo out = {0, 0, nullptr, 0};
    uint8_t *list = self ? *(uint8_t **)((uint8_t *)self + OFF_DATAMANAGER_STAGES) : nullptr;
    if (!list) return out;
    uint8_t *items = *(uint8_t **)(list + OFF_LIST_ITEMS);
    int32_t size = *(int32_t *)(list + OFF_LIST_SIZE);
    if (!items || size <= 0) return out;
    if (index < 0 || index >= size) index = 0;
    memcpy(&out, items + OFF_ARRAY_DATA + (size_t)index * sizeof(StageInfo), sizeof(StageInfo));

    static uint8_t buf[255 * 255];
    uint8_t lava, w, h;
    if (out.tiles && g_array_new_specific && load_test_level(&lava, &w, &h, buf, sizeof(buf))) {
        void *byteArrayClass = *(void **)out.tiles;  // classe byte[] reprise du niveau original
        uint8_t *arr = (uint8_t *)g_array_new_specific(byteArrayClass, (uintptr_t)w * h);
        if (arr) {
            memcpy(arr + OFF_ARRAY_DATA, buf, (size_t)w * h);
            out.width = w; out.height = h; out.tiles = arr; out.lavaSpeed = lava;
            g_testServed++;
            LOGI("niveau de test %dx%d injecté à la place du stage %d", w, h, index + 1);
        }
    }
    return out;
}

// ------------------------------------------------------------------ installation du hook
static void *install_thread(void *) {
    // Le moteur n'est chargé qu'à l'ouverture du jeu : on attend sans limite (l'éditeur peut tourner seul).
    void *h = nullptr;
    while (!(h = dlopen("libil2cpp.so", RTLD_NOW | RTLD_NOLOAD))) usleep(250000);

    void *anySym = dlsym(h, "il2cpp_domain_get");
    g_array_new_specific = (array_new_specific_t)dlsym(h, "il2cpp_array_new_specific");
    Dl_info info;
    if (!anySym || !g_array_new_specific || !dladdr(anySym, &info)) { LOGE("symboles il2cpp introuvables"); return nullptr; }

    uint8_t *base = (uint8_t *)info.dli_fbase;
    if (memcmp(base + RVA_STAGEINFO, EXPECTED_PATCH, sizeof(EXPECTED_PATCH)) != 0) {
        LOGE("libil2cpp.so non patchée ou mauvaise version du jeu");
        return nullptr;
    }
    void *volatile *slot = (void *volatile *)(base + RVA_SLOT);
    *slot = (void *)&hook_StageInfo;
    __atomic_thread_fence(__ATOMIC_SEQ_CST);
    g_hooked = 1;
    LOGI("hook actif (libil2cpp @ %p)", info.dli_fbase);
    return nullptr;
}

// ------------------------------------------------------------------ JNI
extern "C" JNIEXPORT void JNICALL
Java_com_sky_totmeditor_NativeBridge_init(JNIEnv *env, jclass, jstring filesDir) {
    const char *p = env->GetStringUTFChars(filesDir, nullptr);
    snprintf(g_filesDir, sizeof(g_filesDir), "%s", p);
    env->ReleaseStringUTFChars(filesDir, p);
    static bool started = false;
    if (started) return;
    started = true;
    pthread_t t;
    pthread_create(&t, nullptr, install_thread, nullptr);
    pthread_detach(t);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sky_totmeditor_NativeBridge_status(JNIEnv *env, jclass) {
    char s[640];
    snprintf(s, sizeof(s), "%s%s | niveaux de test servis : %d", g_hooked ? "[OK] " : "[--] ", g_status, g_testServed);
    return env->NewStringUTF(s);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sky_totmeditor_NativeBridge_isHooked(JNIEnv *, jclass) {
    return g_hooked ? JNI_TRUE : JNI_FALSE;
}
