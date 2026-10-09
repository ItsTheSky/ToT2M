// libtotmeditor.so : éditeur de niveaux pour Tomb of the Mask 1.2.28 (arm64).
//
// libil2cpp.so est patchée statiquement (tools/patch_il2cpp.py, tools/hooks.json) : quelques fonctions
// lisent un pointeur dans un slot de données (fin du .bss) et y sautent s'il est non nul ; sinon
// elles s'exécutent exactement comme à l'origine. Cette lib se contente d'écrire ses hooks dans les
// slots : aucune page de code n'est modifiée à l'exécution (pas de souci SELinux execmod).
//
// Hooks :
//   DataManager.StageInfo(int)          sert le niveau de test (<filesDir>/test_level.bin)
//   GameStateController.Update()        « tick » sur le thread Unity : lancement direct, commandes
//   GameController.StageCompleted()     victoire pendant un test : interceptée (rien n'est enregistré)
//   GameController.ProcessPlayerDeath() mort pendant un test : interceptée (ni réanimation ni défaite)
//   GameController.SaveGameResults()    jamais exécutée pendant une session de test
// Le code managé est appelé par l'API IL2CPP exportée (noms de classes et de méthodes), toujours
// depuis le thread Unity (dans un hook).

#include <jni.h>
#include <dlfcn.h>
#include <pthread.h>
#include <unistd.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <stdarg.h>
#include <atomic>
#include <android/log.h>
#include "hooks_gen.h"

#define TAG "TotMEditor"
#define LOGI(...) log_status(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOGE(...) log_status(ANDROID_LOG_ERROR, __VA_ARGS__)

static const uint32_t EXPECTED_STAGEINFO_PATCH[4] = {0xb00071b0, 0xf9452a10, 0xb4000050, 0xd61f0200};

// Disposition mémoire (dump IL2CPP)
static const size_t OFF_DATAMANAGER_STAGES = 0x20;  // List<StageInfo> stages
static const size_t OFF_LIST_ITEMS = 0x10;          // StageInfo[] _items
static const size_t OFF_LIST_SIZE = 0x18;           // int _size
static const size_t OFF_ARRAY_DATA = 0x20;          // début des éléments d'un tableau IL2CPP
static const size_t OFF_GSC_GAMESTATE = 0x30;       // GameStateController._gameState (byte)

enum GameState { GS_UNDEFINED = 0, GS_MAIN_MENU = 1, GS_GAME_OVER = 2, GS_GAME_ACTIVE = 3 };

struct StageInfo {      // 0x18 octets
    int32_t width;      // 0x0
    int32_t height;     // 0x4
    void *tiles;        // 0x8  byte[]
    uint8_t lavaSpeed;  // 0x10
};
static_assert(sizeof(StageInfo) == 0x18, "taille StageInfo");

// ------------------------------------------------------------------ API IL2CPP (résolue par dlsym)
struct Il2CppApi {
    void *(*domain_get)();
    void **(*domain_get_assemblies)(void *domain, size_t *size);
    void *(*assembly_get_image)(void *assembly);
    const char *(*image_get_name)(void *image);
    void *(*class_from_name)(void *image, const char *ns, const char *name);
    void *(*class_get_method_from_name)(void *klass, const char *name, int argc);
    void *(*runtime_invoke)(void *method, void *obj, void **params, void **exc);
    void *(*object_unbox)(void *obj);
    void *(*class_get_type)(void *klass);
    void *(*type_get_object)(const void *type);
    void *(*array_new_specific)(void *arrayClass, uintptr_t length);
};
static Il2CppApi api;

// ------------------------------------------------------------------ état
enum TestState {
    T_IDLE = 0,       // pas de test : le jeu est strictement normal
    T_WAIT_MENU = 1,  // test demandé, on attend que le jeu soit au menu pour lancer le stage support
    T_LAUNCHED = 2,   // lancement demandé au jeu, on attend que GenerateStage serve le niveau
    T_RUNNING = 3,    // le niveau de test est joué
    T_WON = 4,        // sortie atteinte (interceptée)
    T_DEAD = 5,       // mort (interceptée)
    T_EXITING = 6,    // retour à l'éditeur demandé
    T_MANUAL = 7,     // lancement auto impossible ou désactivé : lancer un stage à la main
};
enum LaunchMode { L_AUTO = 0, L_PLAY_STORY = 1, L_BTN_LEVEL = 2, L_MANUAL = 3 };
enum Command { C_NONE = 0, C_REPLAY = 1, C_EXIT = 2 };
static const char *STATE_NAMES[] = {"inactif", "attente du menu", "lancement", "en jeu", "gagné", "perdu", "retour", "manuel"};

static char g_filesDir[512];
static char g_status[512] = "lib chargée, en attente du lancement du jeu";
static uint8_t *g_base = nullptr;
static volatile int g_hooked = 0;
static volatile uint32_t g_hookMask = 0;
static volatile int g_testServed = 0;
static std::atomic<int> g_state{T_IDLE};
static std::atomic<int> g_cmd{C_NONE};
static std::atomic<int> g_cmdAck{0};
static std::atomic<int> g_session{0};  // un niveau de test a été servi depuis beginTest : sauvegardes bloquées
static int g_mode = L_AUTO, g_stage = 0;
static int g_frame = 0, g_stateFrame = 0, g_stable = 0, g_attempt = 0, g_servedAtLaunch = 0, g_pressPlayAt = -1;
static bool g_exitIssued = false;
static std::atomic<int> g_saveBlocked{0};

static void log_status(int prio, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(g_status, sizeof(g_status), fmt, ap);
    va_end(ap);
    __android_log_print(prio, TAG, "%s", g_status);
}

static void set_state(int s) {
    if (g_state.load() != s) __android_log_print(ANDROID_LOG_INFO, TAG, "test : %s -> %s", STATE_NAMES[g_state.load()], STATE_NAMES[s]);
    g_state = s;
    g_stateFrame = g_frame;
}

// ------------------------------------------------------------------ appels managés
static void *g_imageCSharp = nullptr, *g_imageCore = nullptr;

static void *find_image(const char *name) {
    if (!api.domain_get || !api.domain_get_assemblies) return nullptr;
    size_t n = 0;
    void **as = api.domain_get_assemblies(api.domain_get(), &n);
    for (size_t i = 0; as && i < n; i++) {
        void *img = api.assembly_get_image(as[i]);
        const char *nm = img ? api.image_get_name(img) : nullptr;
        if (nm && strcmp(nm, name) == 0) return img;
    }
    return nullptr;
}

static void *klass(const char *name) {
    if (!g_imageCSharp) g_imageCSharp = find_image("Assembly-CSharp.dll");
    return g_imageCSharp ? api.class_from_name(g_imageCSharp, "", name) : nullptr;
}

static void *method(const char *cls, const char *name, int argc) {
    void *k = klass(cls);
    void *m = k ? api.class_get_method_from_name(k, name, argc) : nullptr;
    if (!m) __android_log_print(ANDROID_LOG_ERROR, TAG, "méthode introuvable : %s.%s/%d", cls, name, argc);
    return m;
}

// Appelle obj.cls::name(args) ; renvoie false si la méthode manque ou lève une exception.
static bool invoke(const char *cls, const char *name, void *obj, int argc, void **args, void **ret = nullptr) {
    void *m = method(cls, name, argc);
    if (!m || !api.runtime_invoke) return false;
    void *exc = nullptr;
    void *r = api.runtime_invoke(m, obj, args, &exc);
    if (exc) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "exception dans %s.%s", cls, name);
        return false;
    }
    if (ret) *ret = r;
    return true;
}

static bool invoke_int_ret(const char *cls, const char *name, void *obj, int *out) {
    void *r = nullptr;
    if (!invoke(cls, name, obj, 0, nullptr, &r) || !r || !api.object_unbox) return false;
    *out = *(int32_t *)api.object_unbox(r);
    return true;
}

static void *data_manager() {
    void *r = nullptr;
    // HMSingleton<DataManager>.get_Instance, trouvée en remontant aux classes parentes
    return invoke("DataManager", "get_Instance", nullptr, 0, nullptr, &r) ? r : nullptr;
}

static void *find_object_of_type(const char *cls) {
    if (!g_imageCore) g_imageCore = find_image("UnityEngine.CoreModule.dll");
    void *k = klass(cls);
    if (!g_imageCore || !k || !api.class_get_type || !api.type_get_object) return nullptr;
    void *objCls = api.class_from_name(g_imageCore, "UnityEngine", "Object");
    void *m = objCls ? api.class_get_method_from_name(objCls, "FindObjectOfType", 1) : nullptr;
    if (!m) return nullptr;
    void *type = api.type_get_object(api.class_get_type(k));
    void *args[1] = {type};
    void *exc = nullptr;
    void *r = api.runtime_invoke(m, nullptr, args, &exc);
    return exc ? nullptr : r;
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

// GenerateStage vient de décompter une énergie (sauf tutoriel) : on la rend pendant un test.
static void refund_energy() {
    void *dm = data_manager();
    int tuto = 0, energy = 0;
    if (!dm) return;
    void *r = nullptr;
    if (invoke("DataManager", "get_showTutorial", dm, 0, nullptr, &r) && r && api.object_unbox) tuto = *(uint8_t *)api.object_unbox(r);
    if (tuto || !invoke_int_ret("DataManager", "get_energyCount", dm, &energy)) return;
    int v = energy + 1;
    void *args[1] = {&v};
    if (invoke("DataManager", "set_energyCount", dm, 1, args)) __android_log_print(ANDROID_LOG_INFO, TAG, "énergie rendue (%d -> %d)", energy, v);
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
    if (out.tiles && api.array_new_specific && load_test_level(&lava, &w, &h, buf, sizeof(buf))) {
        void *byteArrayClass = *(void **)out.tiles;  // classe byte[] reprise du niveau original
        uint8_t *arr = (uint8_t *)api.array_new_specific(byteArrayClass, (uintptr_t)w * h);
        if (arr) {
            memcpy(arr + OFF_ARRAY_DATA, buf, (size_t)w * h);
            out.width = w; out.height = h; out.tiles = arr; out.lavaSpeed = lava;
            g_testServed++;
            LOGI("niveau de test %dx%d injecté à la place du stage %d", w, h, index + 1);
            if (g_state.load() != T_IDLE) {
                g_session = 1;
                set_state(T_RUNNING);
                refund_energy();
            }
        }
    }
    return out;
}

// ------------------------------------------------------------------ hooks génériques
static void *g_origOverride[HOOK_COUNT];  // tests sur l'hôte uniquement
template <typename F> static F orig(int hook) {
    return g_origOverride[hook] ? (F)g_origOverride[hook] : (F)(g_base + TOTM_HOOKS[hook].tramp);
}

// Fin de partie pendant un test : on garde le « visuel » de ProcessPlayerDeath (désassemblé :
// ShakeGame, BlinkGame, ActivateLava(false), RemoveAllPowerups) sans SaveGameResults ni popup.
// SetPause n'écrit qu'un drapeau ignoré par Update/FixedUpdate : la lave s'arrête via ActivateLava(false).
static void freeze_game(void *gc, bool shake) {
    bool t = true, f = false;
    void *at[1] = {&t};
    void *af[1] = {&f};
    invoke("GameController", "DisableTouches", gc, 1, at);
    invoke("GameController", "ActivateLava", gc, 1, af);
    invoke("GameController", "RemoveAllPowerups", gc, 0, nullptr);
    if (shake) {
        float amp = 1.f;
        void *as[2] = {&amp, &f};
        invoke("GameController", "ShakeGameWithRotation", gc, 2, as);
    }
}

static bool launch_attempt(void *gsc) {
    void *dm = data_manager();
    if (!dm) { LOGE("DataManager introuvable"); return false; }
    int st = g_stage;
    bool no = false;
    void *a1[1] = {&st};
    void *a2[1] = {&no};
    invoke("DataManager", "set_activeStage", dm, 1, a1);
    invoke("DataManager", "set_arcadeActive", dm, 1, a2);
    g_servedAtLaunch = g_testServed;
    bool ok = false;
    if (g_attempt == L_PLAY_STORY) {
        ok = invoke("GameStateController", "PlayStoryGame", gsc, 0, nullptr);
        LOGI("lancement : GameStateController.PlayStoryGame() stage support %d %s", st + 1, ok ? "ok" : "échec");
    } else {
        void *goc = find_object_of_type("GameOverController");
        ok = goc && invoke("GameOverController", "BtnPlayLevelPressed", goc, 1, a1);
        g_pressPlayAt = ok ? g_frame + 45 : -1;
        LOGI("lancement : GameOverController.BtnPlayLevelPressed(%d) %s", st, ok ? "ok" : "échec");
    }
    return ok;
}

static void next_attempt_or_manual(void *gsc) {
    if (g_mode == L_AUTO && g_attempt == L_PLAY_STORY) {
        g_attempt = L_BTN_LEVEL;
        if (launch_attempt(gsc)) { set_state(T_LAUNCHED); return; }
    }
    set_state(T_MANUAL);
    LOGI("lancement automatique impossible : lance un stage à la main, ton niveau sera joué");
}

// Appelé à chaque frame sur le thread Unity, après le Update d'origine.
static void test_tick(void *gsc) {
    g_frame++;
    int st = g_state.load();
    uint8_t gs = *((uint8_t *)gsc + OFF_GSC_GAMESTATE);

    int cmd = g_cmd.exchange(C_NONE);
    if (cmd == C_EXIT) {
        if (gs == GS_GAME_ACTIVE) invoke("GameStateController", "PrepareToExit", gsc, 0, nullptr);
        set_state(T_EXITING);
        g_cmdAck++;
        return;
    }
    if (cmd == C_REPLAY) {
        g_cmdAck++;
        g_attempt = g_mode == L_BTN_LEVEL ? L_BTN_LEVEL : L_PLAY_STORY;
        g_servedAtLaunch = g_testServed;
        if (gs == GS_GAME_ACTIVE && invoke("GameStateController", "RestartGame", gsc, 0, nullptr)) {
            LOGI("rejouer : RestartGame()");
            set_state(T_LAUNCHED);
        } else {
            g_exitIssued = false; g_stable = 0;
            set_state(T_WAIT_MENU);
        }
        return;
    }

    switch (st) {
        case T_WAIT_MENU:
            if (g_mode == L_MANUAL) { set_state(T_MANUAL); break; }
            if (gs == GS_GAME_ACTIVE) {
                // un stage normal tournait encore : on quitte d'abord vers le menu
                if (!g_exitIssued && g_frame - g_stateFrame > 20) {
                    g_exitIssued = true;
                    invoke("GameStateController", "PrepareToExit", gsc, 0, nullptr);
                }
                g_stable = 0;
            } else if (gs == GS_GAME_OVER || gs == GS_MAIN_MENU) {
                // menu stable depuis ~0,3 s (hub) ou ~1,5 s (écran titre) : on lance
                if (++g_stable >= (gs == GS_GAME_OVER ? 20 : 90)) {
                    g_stable = 0;
                    if (launch_attempt(gsc)) set_state(T_LAUNCHED); else next_attempt_or_manual(gsc);
                }
            } else {
                g_stable = 0;
            }
            break;
        case T_LAUNCHED:
            if (g_pressPlayAt >= 0 && g_frame >= g_pressPlayAt) {
                g_pressPlayAt = -1;
                void *sic = find_object_of_type("StageInfoController");
                bool ok = sic && invoke("StageInfoController", "BtnPlayPressed", sic, 0, nullptr);
                LOGI("lancement : StageInfoController.BtnPlayPressed() %s", ok ? "ok" : "échec");
            }
            if (g_frame - g_stateFrame > 300) next_attempt_or_manual(gsc);  // ~5 s sans GenerateStage
            break;
        default:
            break;
    }
}

extern "C" __attribute__((visibility("default")))
void hook_GSC_Update(void *self, void *m) {
    orig<void (*)(void *, void *)>(HOOK_GameStateController_Update)(self, m);
    if (self && (g_state.load() != T_IDLE || g_cmd.load() != C_NONE)) test_tick(self);
}

extern "C" __attribute__((visibility("default")))
void hook_StageCompleted(void *self, void *m) {
    if (g_session.load() && g_state.load() == T_RUNNING) {
        set_state(T_WON);
        freeze_game(self, false);
        LOGI("test : sortie atteinte (victoire interceptée, rien n'est enregistré)");
        return;
    }
    orig<void (*)(void *, void *)>(HOOK_GameController_StageCompleted)(self, m);
}

extern "C" __attribute__((visibility("default")))
void hook_ProcessPlayerDeath(void *self, void *killer, void *m) {
    int st = g_state.load();
    if (g_session.load() && (st == T_RUNNING || st == T_DEAD || st == T_WON)) {
        if (st == T_RUNNING) {
            set_state(T_DEAD);
            freeze_game(self, true);
            LOGI("test : mort (interceptée, ni réanimation ni défaite)");
        }
        return;
    }
    orig<void (*)(void *, void *, void *)>(HOOK_GameController_ProcessPlayerDeath)(self, killer, m);
}

extern "C" __attribute__((visibility("default")))
void hook_SaveGameResults(void *self, void *m) {
    if (g_session.load()) {
        g_saveBlocked++;
        __android_log_print(ANDROID_LOG_INFO, TAG, "test : SaveGameResults bloqué");
        return;
    }
    orig<void (*)(void *, void *)>(HOOK_GameController_SaveGameResults)(self, m);
}

static_assert(HOOK_COUNT == 4, "hooks.json et HOOK_IMPLS doivent rester alignés");
static void *const HOOK_IMPLS[HOOK_COUNT] = {
    (void *)&hook_GSC_Update, (void *)&hook_StageCompleted, (void *)&hook_ProcessPlayerDeath, (void *)&hook_SaveGameResults,
};

// ------------------------------------------------------------------ installation
static void resolve_api(void *h) {
#define SYM(field, name) api.field = (decltype(api.field))dlsym(h, name)
    SYM(domain_get, "il2cpp_domain_get");
    SYM(domain_get_assemblies, "il2cpp_domain_get_assemblies");
    SYM(assembly_get_image, "il2cpp_assembly_get_image");
    SYM(image_get_name, "il2cpp_image_get_name");
    SYM(class_from_name, "il2cpp_class_from_name");
    SYM(class_get_method_from_name, "il2cpp_class_get_method_from_name");
    SYM(runtime_invoke, "il2cpp_runtime_invoke");
    SYM(object_unbox, "il2cpp_object_unbox");
    SYM(class_get_type, "il2cpp_class_get_type");
    SYM(type_get_object, "il2cpp_type_get_object");
    SYM(array_new_specific, "il2cpp_array_new_specific");
#undef SYM
}

// Vérifie les octets posés par patch_il2cpp.py puis écrit les pointeurs dans les slots.
static int install_hooks(uint8_t *base) {
    g_base = base;
    if (memcmp(base + TOTM_RVA_STAGEINFO, EXPECTED_STAGEINFO_PATCH, sizeof(EXPECTED_STAGEINFO_PATCH)) != 0) {
        LOGE("libil2cpp.so non patchée ou mauvaise version du jeu");
        return 0;
    }
    if (*(uint32_t *)(base + TOTM_CAVE_BR) != 0xd61f0200) LOGE("cave absente : APK construit avec l'ancien patch ?");
    *(void *volatile *)(base + TOTM_SLOT_BASE) = (void *)&hook_StageInfo;
    uint32_t mask = 0;
    for (int i = 0; i < HOOK_COUNT; i++) {
        const TotmHookDesc &d = TOTM_HOOKS[i];
        const uint32_t *site = (const uint32_t *)(base + d.rva), *cave = (const uint32_t *)(base + d.cave);
        if (*site != d.site_word || cave[0] != d.cave_word0 || cave[1] != d.cave_word1) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "hook %s : octets inattendus, ignoré", d.name);
            continue;
        }
        *(void *volatile *)(base + d.slot) = HOOK_IMPLS[i];
        mask |= 1u << i;
    }
    __atomic_thread_fence(__ATOMIC_SEQ_CST);
    g_hookMask = mask;
    return 1;
}

static void *install_thread(void *) {
    // Le moteur n'est chargé qu'à l'ouverture du jeu : on attend sans limite (l'éditeur peut tourner seul).
    void *h = nullptr;
    while (!(h = dlopen("libil2cpp.so", RTLD_NOW | RTLD_NOLOAD))) usleep(250000);
    resolve_api(h);
    void *anySym = dlsym(h, "il2cpp_domain_get");
    Dl_info info;
    if (!anySym || !api.array_new_specific || !dladdr(anySym, &info)) { LOGE("symboles il2cpp introuvables"); return nullptr; }
    if (!install_hooks((uint8_t *)info.dli_fbase)) return nullptr;
    g_hooked = 1;
    LOGI("hook actif (libil2cpp @ %p, hooks 0x%x/%d)", info.dli_fbase, g_hookMask, HOOK_COUNT);
    return nullptr;
}

// ------------------------------------------------------------------ commandes (thread Java)
static void begin_test(int stage, int mode) {
    g_stage = stage < 0 ? 0 : stage;
    g_mode = mode;
    g_attempt = mode == L_BTN_LEVEL ? L_BTN_LEVEL : L_PLAY_STORY;
    g_session = 0;
    g_exitIssued = false;
    g_stable = 0;
    g_pressPlayAt = -1;
    g_cmd = C_NONE;
    // le jeu n'est peut-être pas encore chargé : le tick prendra le relais dès qu'il tournera.
    // Sans hook de tick (vieil APK), StageInfo sert quand même le niveau : lancement à la main.
    set_state(mode == L_MANUAL ? T_MANUAL : T_WAIT_MENU);
}

static void end_test() {
    g_cmd = C_NONE;
    g_session = 0;
    set_state(T_IDLE);
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
    char s[900];
    snprintf(s, sizeof(s), "%s%s\nhooks : 0x%x/%d · niveaux de test servis : %d · test : %s · sauvegardes bloquées : %d",
             g_hooked ? "[OK] " : "[--] ", g_status, g_hookMask, HOOK_COUNT, g_testServed, STATE_NAMES[g_state.load()], g_saveBlocked.load());
    return env->NewStringUTF(s);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sky_totmeditor_NativeBridge_isHooked(JNIEnv *, jclass) { return g_hooked ? JNI_TRUE : JNI_FALSE; }

extern "C" JNIEXPORT jint JNICALL
Java_com_sky_totmeditor_NativeBridge_hookMask(JNIEnv *, jclass) { return (jint)g_hookMask; }

extern "C" JNIEXPORT void JNICALL
Java_com_sky_totmeditor_NativeBridge_beginTest(JNIEnv *, jclass, jint stage, jint mode) { begin_test(stage, mode); }

extern "C" JNIEXPORT void JNICALL
Java_com_sky_totmeditor_NativeBridge_endTest(JNIEnv *, jclass) { end_test(); }

extern "C" JNIEXPORT jint JNICALL
Java_com_sky_totmeditor_NativeBridge_testState(JNIEnv *, jclass) { return g_state.load(); }

extern "C" JNIEXPORT jint JNICALL
Java_com_sky_totmeditor_NativeBridge_command(JNIEnv *, jclass, jint cmd) {
    g_cmd = cmd;
    return g_cmdAck.load();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sky_totmeditor_NativeBridge_commandAck(JNIEnv *, jclass) { return g_cmdAck.load(); }
