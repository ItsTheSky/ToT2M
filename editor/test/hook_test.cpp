// Test sur l'hôte de libtotmeditor : fausse disposition mémoire IL2CPP et fausse API IL2CPP.
//   g++ -std=c++17 -I test/stub -I test/jni -I build/gen test/hook_test.cpp -o build/hook_test -ldl -lpthread
#include "../native/totmeditor.cpp"
#include <stdlib.h>
#include <assert.h>
#include <string>
#include <vector>
#include <map>

// ------------------------------------------------------------------ fausse API IL2CPP
struct FakeMethod { std::string cls, name; int argc; };
struct FakeClass { std::string name; };
static std::map<std::string, FakeClass> classes;
static std::vector<FakeMethod *> methods;
static std::vector<std::string> calls;
static std::map<std::string, bool> failing;  // méthodes qui lèvent une exception
static uint8_t fakeGsc[0x200], fakeDm[0x100], fakeGoc[0x40], fakeSic[0x40], fakeGc[0x40];
static int dmActiveStage = -1, dmEnergy = 4;
static bool dmArcade = true, sicExists = true;
static int32_t boxInt;
static uint8_t boxBool;
static char asmCS, asmCore, imgCS, imgCore, domainObj;

static void *f_domain_get() { return &domainObj; }
static void **f_domain_get_assemblies(void *, size_t *n) { static void *a[2] = {&asmCore, &asmCS}; *n = 2; return a; }
static void *f_assembly_get_image(void *a) { return a == &asmCS ? (void *)&imgCS : (void *)&imgCore; }
static const char *f_image_get_name(void *i) { return i == &imgCS ? "Assembly-CSharp.dll" : "UnityEngine.CoreModule.dll"; }
static void *f_class_from_name(void *img, const char *ns, const char *n) {
    std::string key = std::string(img == &imgCS ? "cs:" : "core:") + ns + "." + n;
    classes[key].name = n;
    return &classes[key];
}
static void *f_get_method(void *k, const char *n, int argc) {
    auto *m = new FakeMethod{((FakeClass *)k)->name, n, argc};
    methods.push_back(m);
    return m;
}
static void *f_class_get_type(void *k) { return k; }
static void *f_type_get_object(const void *t) { return (void *)t; }
static void *f_unbox(void *o) { return o; }
static void *f_invoke(void *mi, void *obj, void **args, void **exc) {
    FakeMethod *m = (FakeMethod *)mi;
    std::string id = m->cls + "." + m->name;
    calls.push_back(id);
    if (failing[id]) { *exc = (void *)1; return nullptr; }
    if (id == "DataManager.get_Instance") return fakeDm;
    if (id == "DataManager.set_activeStage") dmActiveStage = *(int *)args[0];
    if (id == "DataManager.set_arcadeActive") dmArcade = *(bool *)args[0];
    if (id == "DataManager.get_energyCount") { boxInt = dmEnergy; return &boxInt; }
    if (id == "DataManager.set_energyCount") dmEnergy = *(int *)args[0];
    if (id == "DataManager.get_showTutorial") { boxBool = 0; return &boxBool; }
    if (id == "Object.FindObjectOfType") {
        std::string c = ((FakeClass *)args[0])->name;
        if (c == "GameOverController") return fakeGoc;
        if (c == "StageInfoController") return sicExists ? fakeSic : nullptr;
        return nullptr;
    }
    return nullptr;
}

static int origCalls[HOOK_COUNT];
static void o_update(void *, void *) { origCalls[0]++; }
static void o_completed(void *, void *) { origCalls[1]++; }
static void o_death(void *, void *, void *) { origCalls[2]++; }
static void o_save(void *, void *) { origCalls[3]++; }

static bool called(const char *id) {
    for (auto &c : calls) if (c == id) return true;
    return false;
}
static void ticks(int n) { for (int i = 0; i < n; i++) hook_GSC_Update(fakeGsc, nullptr); }
static void *fake_new(void *klass, uintptr_t n) {
    uint8_t *a = (uint8_t *)calloc(1, 0x20 + n);
    *(void **)a = klass;
    *(uintptr_t *)(a + 0x18) = n;
    return a;
}
static uint8_t fakeByteArrayClass[64];

int main() {
    api.domain_get = f_domain_get; api.domain_get_assemblies = f_domain_get_assemblies;
    api.assembly_get_image = f_assembly_get_image; api.image_get_name = f_image_get_name;
    api.class_from_name = f_class_from_name; api.class_get_method_from_name = f_get_method;
    api.runtime_invoke = f_invoke; api.object_unbox = f_unbox; api.class_get_type = f_class_get_type;
    api.type_get_object = f_type_get_object; api.array_new_specific = fake_new;
    g_origOverride[0] = (void *)o_update; g_origOverride[1] = (void *)o_completed;
    g_origOverride[2] = (void *)o_death; g_origOverride[3] = (void *)o_save;

    // ---- installation : vérification des octets posés par le patch
    {
        size_t sz = TOTM_SLOT_BASE + 0x40;
        uint8_t *base = (uint8_t *)calloc(1, sz);
        memcpy(base + TOTM_RVA_STAGEINFO, EXPECTED_STAGEINFO_PATCH, 16);
        *(uint32_t *)(base + TOTM_CAVE_BR) = 0xd61f0200;
        for (int i = 0; i < HOOK_COUNT; i++) {
            const TotmHookDesc &d = TOTM_HOOKS[i];
            *(uint32_t *)(base + d.rva) = d.site_word;
            ((uint32_t *)(base + d.cave))[0] = d.cave_word0;
            ((uint32_t *)(base + d.cave))[1] = d.cave_word1;
        }
        *(uint32_t *)(base + TOTM_HOOKS[2].rva) ^= 1;  // un site abîmé : ce hook seul est ignoré
        assert(install_hooks(base) == 1);
        assert(g_hookMask == 0xB);
        assert(*(void **)(base + TOTM_SLOT_BASE) == (void *)&hook_StageInfo);
        assert(*(void **)(base + TOTM_HOOKS[0].slot) == (void *)&hook_GSC_Update);
        assert(*(void **)(base + TOTM_HOOKS[2].slot) == nullptr);
        memset(base + TOTM_RVA_STAGEINFO, 0, 16);
        assert(install_hooks(base) == 0);
        printf("installation : vérification des octets OK (hook abîmé ignoré, StageInfo non patchée refusée)\n");
        g_hookMask = 0xF;
    }

    // ---- StageInfo (cas du prototype)
    uint8_t *t0 = (uint8_t *)fake_new(fakeByteArrayClass, 4), *t1 = (uint8_t *)fake_new(fakeByteArrayClass, 6);
    uint8_t *arr = (uint8_t *)calloc(1, 0x20 + 2 * 24);
    StageInfo s0 = {2, 2, t0, 0}, s1 = {3, 2, t1, 13};
    memcpy(arr + 0x20, &s0, 24); memcpy(arr + 0x20 + 24, &s1, 24);
    uint8_t *list = (uint8_t *)calloc(1, 0x20);
    *(void **)(list + 0x10) = arr; *(int32_t *)(list + 0x18) = 2;
    *(void **)(fakeDm + 0x20) = list;
    snprintf(g_filesDir, sizeof g_filesDir, "/tmp/hooktest");
    assert(system("rm -rf /tmp/hooktest; mkdir -p /tmp/hooktest") == 0);
    StageInfo r = hook_StageInfo(fakeDm, 1, nullptr);
    assert(r.width == 3 && r.height == 2 && r.tiles == t1 && r.lavaSpeed == 13);
    r = hook_StageInfo(fakeDm, 99, nullptr);
    assert(r.tiles == t0);
    printf("StageInfo sans fichier : original OK ; index hors limites : stage 0 OK\n");

    // ---- aucun test : tout passe à l'original, aucun appel managé
    ticks(100);
    hook_StageCompleted(fakeGc, nullptr); hook_ProcessPlayerDeath(fakeGc, nullptr, nullptr); hook_SaveGameResults(fakeGc, nullptr);
    assert(origCalls[0] == 100 && origCalls[1] == 1 && origCalls[2] == 1 && origCalls[3] == 1 && calls.empty());
    printf("sans test : 100 ticks + victoire + mort + sauvegarde passent à l'original, 0 appel managé OK\n");

    // ---- test auto : attente du hub puis PlayStoryGame
    FILE *f = fopen("/tmp/hooktest/test_level.bin", "wb");
    uint8_t lv[3 + 6] = {5, 3, 2, 3, 1, 3, 3, 2, 3};
    fwrite(lv, 1, 9, f); fclose(f);
    begin_test(4, L_AUTO);
    fakeGsc[OFF_GSC_GAMESTATE] = GS_GAME_ACTIVE;  // un stage normal tournait
    ticks(25);
    assert(called("GameStateController.PrepareToExit"));
    fakeGsc[OFF_GSC_GAMESTATE] = GS_GAME_OVER;
    ticks(19);
    assert(!called("GameStateController.PlayStoryGame"));
    ticks(1);
    assert(called("GameStateController.PlayStoryGame") && dmActiveStage == 4 && !dmArcade && g_state == T_LAUNCHED);
    fakeGsc[OFF_GSC_GAMESTATE] = GS_GAME_ACTIVE;
    r = hook_StageInfo(fakeDm, 4, nullptr);
    assert(r.width == 3 && r.height == 2 && r.lavaSpeed == 5 && r.tiles != t0 && memcmp((uint8_t *)r.tiles + 0x20, lv + 3, 6) == 0);
    assert(g_state == T_RUNNING && g_session == 1 && dmEnergy == 5);
    printf("test auto : sortie du stage en cours, attente du hub, PlayStoryGame(stage 5), niveau servi, énergie rendue OK\n");

    // ---- pendant le test : sauvegarde bloquée, mort interceptée
    hook_SaveGameResults(fakeGc, nullptr);
    assert(origCalls[3] == 1);
    calls.clear();
    hook_ProcessPlayerDeath(fakeGc, nullptr, nullptr);
    assert(origCalls[2] == 1 && g_state == T_DEAD && called("GameController.SetPause") && called("GameController.DisableTouches"));
    hook_ProcessPlayerDeath(fakeGc, nullptr, nullptr);
    assert(origCalls[2] == 1);
    printf("pendant le test : SaveGameResults bloqué, mort interceptée (pause, touches coupées) OK\n");

    // ---- rejouer : RestartGame puis victoire interceptée
    int ack = g_cmdAck;
    g_cmd = C_REPLAY;
    ticks(1);
    assert(g_cmdAck == ack + 1 && called("GameStateController.RestartGame") && g_state == T_LAUNCHED);
    hook_StageInfo(fakeDm, 4, nullptr);
    assert(g_state == T_RUNNING);
    hook_StageCompleted(fakeGc, nullptr);
    assert(origCalls[1] == 1 && g_state == T_WON);
    printf("rejouer : RestartGame, niveau re-servi, victoire interceptée OK\n");

    // ---- retour à l'éditeur
    calls.clear();
    g_cmd = C_EXIT;
    ticks(1);
    assert(called("GameStateController.PrepareToExit") && g_state == T_EXITING && g_cmdAck == ack + 2);
    end_test();
    hook_SaveGameResults(fakeGc, nullptr); hook_StageCompleted(fakeGc, nullptr);
    assert(origCalls[3] == 2 && origCalls[1] == 2 && g_state == T_IDLE);
    printf("retour : PrepareToExit, puis fin du test : sauvegarde et victoire de nouveau normales OK\n");

    // ---- repli : PlayStoryGame en échec -> BtnPlayLevelPressed + BtnPlayPressed -> manuel
    calls.clear();
    failing["GameStateController.PlayStoryGame"] = true;
    begin_test(0, L_AUTO);
    fakeGsc[OFF_GSC_GAMESTATE] = GS_GAME_OVER;
    ticks(20);
    assert(called("GameStateController.PlayStoryGame") && called("GameOverController.BtnPlayLevelPressed") && g_state == T_LAUNCHED);
    ticks(46);
    assert(called("StageInfoController.BtnPlayPressed"));
    ticks(300);
    assert(g_state == T_MANUAL);
    hook_StageInfo(fakeDm, 0, nullptr);
    assert(g_state == T_RUNNING);
    end_test();
    printf("repli : PlayStoryGame en échec -> BtnPlayLevelPressed + BtnPlayPressed -> manuel, niveau servi OK\n");

    // ---- mode manuel
    calls.clear();
    begin_test(0, L_MANUAL);
    ticks(200);
    assert(g_state == T_MANUAL && !called("GameStateController.PlayStoryGame"));
    end_test();
    printf("mode manuel : aucun lancement automatique OK\n");

    // ---- fichier tronqué
    f = fopen("/tmp/hooktest/test_level.bin", "wb"); fwrite(lv, 1, 5, f); fclose(f);
    r = hook_StageInfo(fakeDm, 0, nullptr);
    assert(r.tiles == t0);
    printf("fichier tronqué : repli sur l'original OK (%d niveaux de test servis au total)\n", g_testServed);
    printf("hook_test : OK\n");
    return 0;
}
