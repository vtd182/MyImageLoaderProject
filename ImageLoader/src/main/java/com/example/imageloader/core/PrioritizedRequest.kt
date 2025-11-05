package com.example.imageloader.core

import com.example.imageloader.core.enums.RequestPriority
import com.example.imageloader.target.Target
import kotlinx.coroutines.Job

data class PrioritizedRequest(
    val request: Request,
    val target: Target,
    val priority: RequestPriority,
    val job: Job
)