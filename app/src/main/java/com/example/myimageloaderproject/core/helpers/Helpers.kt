package com.example.myimageloaderproject.core.helpers

import org.json.JSONArray
import org.json.JSONObject

object JsonParser {
    inline fun <T> parseArray(json: String?, map: (JSONObject) -> T): List<T> {
        if (json == null) return emptyList()
        val arr = JSONArray(json)
        return List(arr.length()) { index ->
            map(arr.getJSONObject(index))
        }
    }
}
