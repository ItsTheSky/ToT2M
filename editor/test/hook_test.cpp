#include "../native/totmeditor.cpp"
#include <stdlib.h>
#include <assert.h>
static uint8_t fakeByteArrayClass[64];
static void* fake_new(void* klass, uintptr_t n){ uint8_t* a=(uint8_t*)calloc(1,0x20+n); *(void**)a=klass; *(uintptr_t*)(a+0x18)=n; return a; }
int main(){
  // 2 stages officiels factices
  uint8_t* t0=(uint8_t*)fake_new(fakeByteArrayClass,4); uint8_t* t1=(uint8_t*)fake_new(fakeByteArrayClass,6);
  uint8_t* arr=(uint8_t*)calloc(1,0x20+2*24);
  StageInfo s0={2,2,t0,0}, s1={3,2,t1,13}; memcpy(arr+0x20,&s0,24); memcpy(arr+0x20+24,&s1,24);
  uint8_t* list=(uint8_t*)calloc(1,0x20); *(void**)(list+0x10)=arr; *(int32_t*)(list+0x18)=2;
  uint8_t* dm=(uint8_t*)calloc(1,0x40); *(void**)(dm+0x20)=list;
  g_array_new_specific=fake_new;
  snprintf(g_filesDir,sizeof g_filesDir,"/tmp/hooktest"); system("rm -rf /tmp/hooktest; mkdir -p /tmp/hooktest");
  StageInfo r=hook_StageInfo(dm,1,nullptr); assert(r.width==3&&r.height==2&&r.tiles==t1&&r.lavaSpeed==13); printf("sans fichier: original OK\n");
  r=hook_StageInfo(dm,99,nullptr); assert(r.tiles==t0); printf("index hors limites: stage 0 OK\n");
  FILE* f=fopen("/tmp/hooktest/test_level.bin","wb"); uint8_t lv[3+6]={5,3,2, 3,1,3, 3,2,3}; fwrite(lv,1,9,f); fclose(f);
  r=hook_StageInfo(dm,0,nullptr); assert(r.width==3&&r.height==2&&r.lavaSpeed==5&&r.tiles!=t0);
  assert(*(void**)r.tiles==fakeByteArrayClass && memcmp((uint8_t*)r.tiles+0x20,lv+3,6)==0); printf("avec fichier: niveau injecté OK (%d servis)\n", g_testServed);
  f=fopen("/tmp/hooktest/test_level.bin","wb"); fwrite(lv,1,5,f); fclose(f);
  r=hook_StageInfo(dm,0,nullptr); assert(r.tiles==t0); printf("fichier tronqué: repli sur l'original OK\n");
  return 0;
}
