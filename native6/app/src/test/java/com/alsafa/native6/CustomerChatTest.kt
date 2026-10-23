package com.alsafa.native6

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CustomerChatTest {
    @Test fun syrianLocalNumber() {
        assertEquals("963912345678",CustomerChat.normalizedNumber("0912 345 678"))
        assertEquals("963912345678",CustomerChat.normalizedNumber("+963 912 345 678"))
        assertEquals("963912345678",CustomerChat.normalizedNumber("00963-912-345-678"))
        assertEquals("963912345678",CustomerChat.normalizedNumber("912345678"))
    }
    @Test fun turkishLocalNumber() {
        assertEquals("905321234567",CustomerChat.normalizedNumber("0532 123 45 67"))
        assertEquals("905321234567",CustomerChat.normalizedNumber("+90 532 123 45 67"))
        assertEquals("905321234567",CustomerChat.normalizedNumber("0090 532 123 45 67"))
    }
    @Test fun invalidPhoneRejected() {
        assertThrows(IllegalArgumentException::class.java) { CustomerChat.normalizedNumber("") }
        assertThrows(IllegalArgumentException::class.java) { CustomerChat.normalizedNumber("123") }
        assertThrows(IllegalArgumentException::class.java) { CustomerChat.normalizedNumber("0912abc345678") }
        assertThrows(IllegalArgumentException::class.java) { CustomerChat.normalizedNumber("00963 912 34") }
        assertThrows(IllegalArgumentException::class.java) { CustomerChat.normalizedNumber("0532 123 45") }
        assertThrows(IllegalArgumentException::class.java) { CustomerChat.normalizedNumber("091234567") }
        assertThrows(IllegalArgumentException::class.java) { CustomerChat.normalizedNumber("0912-345-67X") }
    }
}
