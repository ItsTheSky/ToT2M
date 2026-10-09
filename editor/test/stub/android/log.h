#include <stdarg.h>
#pragma once
#include <stdio.h>
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_ERROR 6
static inline int __android_log_print(int p, const char* t, const char* f, ...) { (void)p; printf("[%s] ", t); va_list ap; va_start(ap,f); vprintf(f,ap); va_end(ap); printf("\n"); return 0; }
