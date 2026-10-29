package com.ssafy.eolssu.platform.storage

import com.ssafy.eolssu.core.storage.SecureStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS Keychain에 일반 암호 항목(서비스 [service], 계정은 키)으로 둔다. 기기를 처음 잠금 해제한 뒤부터 읽을 수 있고,
 * 다른 기기로 옮겨지지 않는다(`AfterFirstUnlockThisDeviceOnly`). 값은 UTF-8 문자열이다.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSecureStore(private val service: String = "com.ssafy.eolssu.secure") : SecureStore {

    override fun getString(key: String): String? = memScoped {
        val query = query(key) {
            CFDictionaryAddValue(it, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(it, kSecMatchLimit, kSecMatchLimitOne)
        }
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override fun putString(key: String, value: String) {
        remove(key)
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val dataRef = CFBridgingRetain(data)
        val query = query(key) {
            CFDictionaryAddValue(it, kSecValueData, dataRef)
            CFDictionaryAddValue(it, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        SecItemAdd(query, null)
        CFRelease(query)
        CFRelease(dataRef)
    }

    override fun remove(key: String) {
        val query = query(key) {}
        SecItemDelete(query)
        CFRelease(query)
    }

    /** 이 저장소의 항목을 가리키는 조건. 사전이 값을 붙잡으므로 넣은 뒤 바로 놓는다. 다 쓰면 [CFRelease]한다. */
    private fun query(key: String, extra: (CFMutableDictionaryRef?) -> Unit): CFMutableDictionaryRef? {
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val serviceRef = CFBridgingRetain(service)
        val accountRef = CFBridgingRetain(key)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, serviceRef)
        CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
        CFRelease(serviceRef)
        CFRelease(accountRef)
        extra(query)
        return query
    }
}
