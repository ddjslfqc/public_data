package com.fuusy.hiddendanger.ui.adapter

import com.google.gson.*
import java.lang.reflect.Type

class FormItemDeserializer : JsonDeserializer<DynamicFormAdapter.FormItem> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): DynamicFormAdapter.FormItem {
        val obj = json.asJsonObject

        val key = obj["key"].asString
        val typeStr = obj["type"].asString
        val label = obj["label"].asString
        val isRequired = obj["isRequired"].asBoolean
        val value = obj["value"]?.asString ?: ""
        val placeholder = if (obj.has("placeholder")) obj["placeholder"].asString else null

        // 适配 options 字段（对象数组：{ value, label }），兼容字符串数组降级
        val options = if (obj.has("options") && obj["options"].isJsonArray) {
            obj["options"].asJsonArray.map { optionEl ->
                if (optionEl.isJsonObject) {
                    val optionObj = optionEl.asJsonObject
                    val valueStr = when {
                        optionObj.has("value") -> optionObj["value"].asString
                        optionObj.has("label") -> optionObj["label"].asString
                        else -> ""
                    }
                    val labelStr = when {
                        optionObj.has("label") -> optionObj["label"].asString
                        optionObj.has("value") -> optionObj["value"].asString
                        else -> valueStr
                    }
                    DynamicFormAdapter.OptionItem(value = valueStr, label = labelStr)
                } else {
                    val primitive = optionEl.asString
                    DynamicFormAdapter.OptionItem(value = primitive, label = primitive)
                }
            }
        } else null

        // 字符串转Int映射
        val typeInt = when (typeStr) {
            "INPUT_TEXT" -> DynamicFormAdapter.VIEW_TYPE_INPUT_TEXT
            "SELECTOR" -> DynamicFormAdapter.VIEW_TYPE_SELECTOR
            // 你可以根据实际类型继续补充
            else -> -1
        }

        return DynamicFormAdapter.FormItem(
            key = key,
            type = typeInt,
            label = label,
            isRequired = isRequired,
            value = value,
            placeholder = placeholder,
            options = options
        )
    }
} 