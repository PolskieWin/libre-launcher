//
// Created by maks on 19.06.2023.
//

#define _GNU_SOURCE

#include <linux/limits.h>
#include <stdbool.h>
#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sched.h>
#include <string.h>
#include <pthread.h>
#include <jni.h>

static _Thread_local bool big_core_affine = false;

static cpu_set_t bigcore_affinity_set;
static bool has_affinity_set;
static pthread_once_t s_affinity_once = PTHREAD_ONCE_INIT;

#define FREQ_MAX 256
#define MAX_CORES 32

void bigcore_format_cpu_path(char* buffer, unsigned int cpu_core) {
    snprintf(buffer, PATH_MAX, "/sys/devices/system/cpu/cpu%i/cpufreq/cpuinfo_max_freq", cpu_core);
}

static void create_affinity_set() {
    char path_buffer[PATH_MAX];
    char freq_buffer[FREQ_MAX];
    char* discard;
    unsigned long core_freq[MAX_CORES];
    unsigned long max_freq = 0;
    unsigned long min_freq = ~0UL;
    unsigned int corecnt = 0;

    memset(core_freq, 0, sizeof(core_freq));

    while(corecnt < MAX_CORES) {
        bigcore_format_cpu_path(path_buffer, corecnt);
        int corefreqfd = open(path_buffer, O_RDONLY);
        if(corefreqfd != -1) {
            ssize_t read_count = read(corefreqfd, freq_buffer, FREQ_MAX - 1);
            close(corefreqfd);
            if (read_count > 0) {
                freq_buffer[read_count] = 0;
                unsigned long freq = strtoul(freq_buffer, &discard, 10);
                core_freq[corecnt] = freq;
                if(freq > max_freq) max_freq = freq;
                if(freq < min_freq) min_freq = freq;
            }
        }else{
            break;
        }
        corecnt++;
    }

    CPU_ZERO(&bigcore_affinity_set);
    unsigned int set_count = 0;

    // Threshold: include all cores whose frequency is closer to max than min (performance + prime cores)
    unsigned long threshold = (min_freq != ~0UL && max_freq > min_freq) ? (min_freq + (max_freq - min_freq) / 2) : max_freq;
    for(unsigned int i = 0; i < corecnt; i++) {
        if(core_freq[i] >= threshold && core_freq[i] > 0) {
            CPU_SET_S(i, CPU_SETSIZE, &bigcore_affinity_set);
            set_count++;
        }
    }

    if(set_count == 0 && corecnt > 0) {
        for(unsigned int i = 0; i < corecnt; i++) {
            CPU_SET_S(i, CPU_SETSIZE, &bigcore_affinity_set);
        }
    }

    printf("bigcore: selected %u performance/prime CPU cores (max freq: %lu Hz)\n", set_count, max_freq);
    has_affinity_set = true;
}

void make_big_core_affine() {
    if(big_core_affine) return;
    pthread_once(&s_affinity_once, create_affinity_set);
    int result = sched_setaffinity(0, CPU_SETSIZE, &bigcore_affinity_set);
    if(result != 0) {
        printf("bigcore: setting affinity failed: %s\n", strerror(result));
    }else{
        printf("bigcore: forced current thread onto big core\n");
        big_core_affine = true;
    }
}

void make_all_cores_affine() {
    cpu_set_t all_cores;
    CPU_ZERO(&all_cores);
    long num_cores = sysconf(_SC_NPROCESSORS_CONF);
    if(num_cores <= 0) num_cores = 8;
    for(int i = 0; i < num_cores && i < MAX_CORES; i++) {
        CPU_SET_S(i, CPU_SETSIZE, &all_cores);
    }
    sched_setaffinity(0, CPU_SETSIZE, &all_cores);
    big_core_affine = false;
    printf("bigcore: relaxed affinity to all cores for low-power operation\n");
}

__attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_git_artdeell_mojoexec_MojoExec_setUseBigCoreAffinity(JNIEnv *env, jclass clazz, jboolean useBigCore) {
    if(useBigCore == JNI_TRUE) {
        make_big_core_affine();
    } else {
        make_all_cores_affine();
    }
}
