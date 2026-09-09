//go:build android && cgo
// SPDX-License-Identifier: GPL-3.0-or-later
package main
/*
#include <sys/prctl.h>
#include <signal.h>
#include <unistd.h>
static void parvaz_parent_guard(void) {
    pid_t parent=getppid();
    if(parent<=1 || prctl(PR_SET_PDEATHSIG,SIGKILL)!=0 || getppid()!=parent) _exit(70);
}
*/
import "C"
func init() { C.parvaz_parent_guard() }
