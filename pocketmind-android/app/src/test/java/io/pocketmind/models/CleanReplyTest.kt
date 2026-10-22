package io.pocketmind.models

import io.pocketmind.ui.chat.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class CleanReplyTest {
    @Test
    fun `reasoning blocks are hidden`() {
        assertEquals("Ответ", ChatViewModel.cleanReply("<think>\nхм\n</think>\n\nОтвет"))
        assertEquals("", ChatViewModel.cleanReply("<think>ещё думаю"))
        assertEquals("Привет", ChatViewModel.cleanReply("Привет"))
    }
}
