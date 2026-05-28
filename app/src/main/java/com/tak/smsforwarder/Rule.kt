package com.tak.smsforwarder

data class Rule(
    val id: Long,
    val name: String,
    val sender: String,
    val keyword: String,
    val target: String,
    val suffix: String,
    val enabled: Boolean,
    val allowOtp: Boolean
)
