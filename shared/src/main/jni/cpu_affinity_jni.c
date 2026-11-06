#define _GNU_SOURCE

#include <jni.h>
#include <sched.h>
#include <unistd.h>

/*
 * Expand the calling thread to every CPU online at this moment. Android can still
 * narrow this through cpusets or vendor policy, so report the mask that took effect.
 */
JNIEXPORT jint JNICALL
Java_com_shilapi_xcertplay_media_CpuAffinity_nativeUseAllCores(
        JNIEnv *env, jclass type) {
    (void) env;
    (void) type;

    const long configured = sysconf(_SC_NPROCESSORS_CONF);
    if (configured <= 0 || configured > CPU_SETSIZE) return -1;

    cpu_set_t requested;
    CPU_ZERO(&requested);
    for (long cpu = 0; cpu < configured; cpu++) CPU_SET((int) cpu, &requested);
    if (sched_setaffinity(0, sizeof(requested), &requested) != 0) return -1;

    cpu_set_t effective;
    CPU_ZERO(&effective);
    if (sched_getaffinity(0, sizeof(effective), &effective) != 0) return -1;
    return CPU_COUNT(&effective);
}
