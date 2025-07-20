package com.japyi0210.simpleenglishdictation.model

import java.util.Date

/**
 * ChatGPT와의 질문/응답 메시지를 나타내는 데이터 클래스
 * @param isUser 사용자가 보낸 메시지 여부 (true: 사용자, false: GPT)
 * @param message 메시지 내용
 * @param timestamp 메시지 생성 시간 (기본값: 현재 시간)
 */
data class ChatMessage(
    val isUser: Boolean,
    val message: String,
    val timestamp: Date = Date()
)
