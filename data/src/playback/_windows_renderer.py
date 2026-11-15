"""Own a Windows renderer tree, including Store Python descendants."""
import ctypes
from ctypes import wintypes
import math
import os
import subprocess
import sys


class _BasicLimits(ctypes.Structure):
    _fields_ = [('process_time', ctypes.c_longlong), ('job_time', ctypes.c_longlong),
                ('flags', wintypes.DWORD), ('minimum_working_set', ctypes.c_size_t),
                ('maximum_working_set', ctypes.c_size_t), ('active_process_limit', wintypes.DWORD),
                ('affinity', ctypes.c_size_t), ('priority', wintypes.DWORD), ('scheduling', wintypes.DWORD)]


class _IoCounters(ctypes.Structure):
    _fields_ = [(name, ctypes.c_ulonglong) for name in
                ('reads', 'writes', 'other', 'read_bytes', 'write_bytes', 'other_bytes')]


class _ExtendedLimits(ctypes.Structure):
    _fields_ = [('basic', _BasicLimits), ('io', _IoCounters),
                ('process_memory', ctypes.c_size_t), ('job_memory', ctypes.c_size_t),
                ('peak_process_memory', ctypes.c_size_t), ('peak_job_memory', ctypes.c_size_t)]


class _StartupInfo(ctypes.Structure):
    _fields_ = [('size', wintypes.DWORD), ('reserved', wintypes.LPWSTR),
                ('desktop', wintypes.LPWSTR), ('title', wintypes.LPWSTR),
                *[(name, wintypes.DWORD) for name in ('x', 'y', 'width', 'height',
                                                   'columns', 'rows', 'fill', 'flags')],
                ('show', wintypes.WORD), ('reserved_size', wintypes.WORD),
                ('reserved_data', ctypes.c_void_p), ('stdin', wintypes.HANDLE),
                ('stdout', wintypes.HANDLE), ('stderr', wintypes.HANDLE)]


class _StartupInfoEx(ctypes.Structure):
    _fields_ = [('startup', _StartupInfo), ('attributes', ctypes.c_void_p)]


class _ProcessInfo(ctypes.Structure):
    _fields_ = [('process', wintypes.HANDLE), ('thread', wintypes.HANDLE),
                ('pid', wintypes.DWORD), ('tid', wintypes.DWORD)]


class _SecurityAttributes(ctypes.Structure):
    _fields_ = [('size', wintypes.DWORD), ('descriptor', ctypes.c_void_p), ('inherit', wintypes.BOOL)]


def _kernel():
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    signatures = {
        'CreateJobObjectW': (wintypes.HANDLE, [ctypes.c_void_p, wintypes.LPCWSTR]),
        'SetInformationJobObject': (wintypes.BOOL, [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]),
        'QueryInformationJobObject': (wintypes.BOOL, [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p,
                                                   wintypes.DWORD, ctypes.c_void_p]),
        'IsProcessInJob': (wintypes.BOOL, [wintypes.HANDLE, wintypes.HANDLE, ctypes.POINTER(wintypes.BOOL)]),
        'TerminateJobObject': (wintypes.BOOL, [wintypes.HANDLE, wintypes.UINT]),
        'OpenProcess': (wintypes.HANDLE, [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]),
        'WaitForSingleObject': (wintypes.DWORD, [wintypes.HANDLE, wintypes.DWORD]),
        'GetExitCodeProcess': (wintypes.BOOL, [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]),
        'CloseHandle': (wintypes.BOOL, [wintypes.HANDLE]),
        'GetModuleFileNameW': (wintypes.DWORD, [wintypes.HANDLE, wintypes.LPWSTR, wintypes.DWORD]),
        'CreateFileW': (wintypes.HANDLE, [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                                        ctypes.c_void_p, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]),
        'InitializeProcThreadAttributeList': (wintypes.BOOL, [ctypes.c_void_p, wintypes.DWORD,
                                                            wintypes.DWORD, ctypes.POINTER(ctypes.c_size_t)]),
        'UpdateProcThreadAttribute': (wintypes.BOOL, [ctypes.c_void_p, wintypes.DWORD, ctypes.c_size_t,
                                                   ctypes.c_void_p, ctypes.c_size_t, ctypes.c_void_p, ctypes.c_void_p]),
        'DeleteProcThreadAttributeList': (None, [ctypes.c_void_p]),
        'CreateProcessW': (wintypes.BOOL, [wintypes.LPCWSTR, wintypes.LPWSTR, ctypes.c_void_p, ctypes.c_void_p,
                                         wintypes.BOOL, wintypes.DWORD, ctypes.c_void_p, wintypes.LPCWSTR,
                                         ctypes.c_void_p, ctypes.c_void_p]),
    }
    for name, (result, arguments) in signatures.items():
        function = getattr(kernel, name)
        function.restype, function.argtypes = result, arguments
    return kernel


def _check(result):
    if not result:
        raise ctypes.WinError(ctypes.get_last_error())
    return result


def _terminate_and_wait(kernel, job):
    members = []
    try:
        # This request is terminal. Prevent new descendants before taking handles.
        # Any live member already fills this limit, regardless of the old count.
        limits = _ExtendedLimits()
        limits.basic.flags = 0x2000 | 0x8  # KILL_ON_JOB_CLOSE | ACTIVE_PROCESS
        limits.basic.active_process_limit = 1
        _check(kernel.SetInformationJobObject(job, 9, ctypes.byref(limits), ctypes.sizeof(limits)))
        size = 4096
        while True:
            processes = ctypes.create_string_buffer(size)
            if kernel.QueryInformationJobObject(job, 3, processes, size, None):
                break
            if ctypes.get_last_error() != 234:  # ERROR_MORE_DATA
                _check(False)
            size *= 2
        count = wintypes.DWORD.from_buffer(processes, 4).value
        for pid in (ctypes.c_size_t * count).from_buffer(processes, 8):
            process = kernel.OpenProcess(0x100000 | 0x1000, False, pid)  # SYNCHRONIZE | QUERY_LIMITED_INFORMATION
            if not process:
                if ctypes.get_last_error() == 87:  # Already gone.
                    continue
                _check(False)
            try:
                member = wintypes.BOOL()
                _check(kernel.IsProcessInJob(process, job, ctypes.byref(member)))
                if member.value:  # Never wait on a reused, unrelated PID.
                    members.append(process)
                    process = None
            finally:
                if process:
                    kernel.CloseHandle(process)
    finally:
        try:
            _check(kernel.TerminateJobObject(job, 1))
            # PID lists / ActiveProcesses can reach zero before open files close.
            for process in members:
                if kernel.WaitForSingleObject(process, 0xFFFFFFFF) != 0:
                    _check(False)
        finally:
            for process in members:
                kernel.CloseHandle(process)


def run_renderer(command, timeout):
    kernel = _kernel()
    job = _check(kernel.CreateJobObjectW(None, None))
    attributes = None
    null = None
    info = _ProcessInfo()
    try:
        limits = _ExtendedLimits()
        limits.basic.flags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        _check(kernel.SetInformationJobObject(job, 9, ctypes.byref(limits), ctypes.sizeof(limits)))
        environment = None
        launch = list(command)
        if os.path.normcase(launch[0]) == os.path.normcase(sys.executable):
            # Bypass this interpreter's redirector, retaining its runtime and venv.
            # __PYVENV_LAUNCHER__ is also used by multiprocessing on Windows.
            executable = ctypes.create_unicode_buffer(32768)
            length = _check(kernel.GetModuleFileNameW(None, executable, len(executable)))
            if length >= len(executable):
                raise OSError('Python executable path is too long')
            launch[0] = executable.value
            env = {**os.environ, '__PYVENV_LAUNCHER__': sys.executable}
            environment = ctypes.create_unicode_buffer('\0'.join(f'{key}={value}' for key, value in
                                                                  sorted(env.items(), key=lambda item: item[0].upper())) + '\0')
        security = _SecurityAttributes(ctypes.sizeof(_SecurityAttributes), None, True)
        null = kernel.CreateFileW('NUL', 0xC0000000, 3, ctypes.byref(security), 3, 0x80, None)
        if null == ctypes.c_void_p(-1).value:
            null = None
            _check(False)
        size = ctypes.c_size_t()
        kernel.InitializeProcThreadAttributeList(None, 3, 0, ctypes.byref(size))
        buffer = ctypes.create_string_buffer(size.value)
        _check(kernel.InitializeProcThreadAttributeList(buffer, 3, 0, ctypes.byref(size)))
        attributes = buffer
        jobs, handles = (wintypes.HANDLE * 1)(job), (wintypes.HANDLE * 1)(null)
        # Store Python otherwise starts descendants outside the assigned job.
        # DESKTOP_APP_BREAKAWAY_DISABLE_PROCESS_TREE retains this runtime boundary.
        desktop_policy = wintypes.DWORD(2)
        for key, value in ((0x2000D, jobs), (0x20002, handles), (0x20012, desktop_policy)):
            _check(kernel.UpdateProcThreadAttribute(attributes, 0, key, ctypes.byref(value),
                                                   ctypes.sizeof(value), None, None))
        startup = _StartupInfoEx()
        startup.startup.size = ctypes.sizeof(startup)
        startup.startup.flags = 0x100  # STARTF_USESTDHANDLES
        startup.startup.stdin = startup.startup.stdout = startup.startup.stderr = null
        startup.attributes = ctypes.addressof(attributes)
        # NO_WINDOW | EXTENDED_STARTUPINFO_PRESENT | UNICODE_ENVIRONMENT.
        _check(kernel.CreateProcessW(None, ctypes.create_unicode_buffer(subprocess.list2cmdline(launch)),
                                     None, None, True, 0x08080400, environment, None,
                                     ctypes.byref(startup), ctypes.byref(info)))
        kernel.CloseHandle(info.thread)
        info.thread = None
        result = kernel.WaitForSingleObject(info.process, math.ceil(timeout * 1000))
        if result == 258:  # WAIT_TIMEOUT
            raise subprocess.TimeoutExpired(command, timeout)
        if result != 0:
            _check(False)
        status = wintypes.DWORD()
        _check(kernel.GetExitCodeProcess(info.process, ctypes.byref(status)))
        return status.value
    finally:
        try:
            _terminate_and_wait(kernel, job)
        finally:
            for handle in (info.thread, info.process, null, job):
                if handle:
                    kernel.CloseHandle(handle)
            if attributes is not None:
                kernel.DeleteProcThreadAttributeList(attributes)
