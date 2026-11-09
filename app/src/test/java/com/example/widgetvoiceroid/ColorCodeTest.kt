package com.example.widgetvoiceroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorCodeTest {

    @Test
    fun カラーコードを読む() {
        assertEquals(0xFFFFB3BE.toInt(), ColorCode.parse("#FFB3BE"))
        assertEquals(0xFFFFB3BE.toInt(), ColorCode.parse(" ffb3be "))
        assertEquals(0xFFFFB3BE.toInt(), ColorCode.parse("＃FFB3BE"))
        assertEquals(0xFFFFB3BE.toInt(), ColorCode.parse("＃ＦＦＢ３ＢＥ"))
        assertEquals(0xFFC9F2A0.toInt(), ColorCode.parse("ｃ９ｆ２ａ０"))
        assertEquals(0xFFFFBB33.toInt(), ColorCode.parse("#FB3"))
        assertEquals(0xFF000000.toInt(), ColorCode.parse("#000000"))
    }

    @Test
    fun 読めないカラーコードはnull() {
        assertNull(ColorCode.parse(""))
        assertNull(ColorCode.parse("#FFB3B"))
        assertNull(ColorCode.parse("#GGGGGG"))
        assertNull(ColorCode.parse("#FFFFB3BE"))
    }

    @Test
    fun カラーコードに直す() {
        assertEquals("#FFB3BE", ColorCode.format(0xFFFFB3BE.toInt()))
        assertEquals("#00000A", ColorCode.format(0xFF00000A.toInt()))
    }
}
