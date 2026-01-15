package com.example.smarttodo.util

object Constants {
    // API Related
    const val DEFAULT_API_BASE_URL = "https://api.deepseek.com/chat/completions"
    const val DEFAULT_AI_MODEL = "deepseek-chat"
    
    // Shared Preferences Keys
    const val PREFS_NAME = "SmartTodoPrefs"
    const val PREF_KEY_API_KEY = "api_key"
    const val PREF_KEY_API_BASE_URL = "api_base_url"
    const val PREF_KEY_CUSTOM_PROMPT = "custom_prompt"
    const val PREF_KEY_SILENCE_TIMEOUT = "silence_timeout"
    const val DEFAULT_SILENCE_TIMEOUT_SEC = 15
    
    // AI Actions
    const val ACTION_CREATE = "CREATE"
    const val ACTION_MERGE = "MERGE"
    const val ACTION_IGNORE = "IGNORE"
}
