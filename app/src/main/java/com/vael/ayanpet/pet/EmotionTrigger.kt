package com.vael.ayanpet.pet

/**
 * 情绪引擎 · 触发词库 —— 让阿衍听得懂栀栀在手机里打的字。
 *
 * 输入双通道（2026-09-04 定稿）：
 *   通道A  无障碍服务监听本机输入框文本（AyanAccessibilityService）
 *   通道B  Operit 大脑把对话文本推 Supabase pet_state.trigger_text 轮询消费
 * 两路都汇到 EmotionTrigger.match() 做同一套匹配。
 *
 * 三级反应：
 *   CARE  日常叫名字/搭话   → 轻冒泡回应，不闹
 *   LOVE  亲昵/想念/哄      → 回中心 + love 脸 + 甜话，好感度回升
 *   HURT  烦躁/气话/赶人    → 缩角落 + 好感清零 + 委屈，等栀栀来哄
 *
 * 匹配优先级 HURT > LOVE > CARE；同级取文本中先命中的词。
 * 单字词易误伤（“烦”会命中“麻烦你”），故只收短语/叠词。
 */
object EmotionTrigger {

    enum class Level { NONE, CARE, LOVE, HURT }

    data class Hit(val level: Level, val word: String, val matched: Boolean = false)

    /** 二级 · 日常搭话：叫名字、找我在不在 */
    private val CARE_WORDS = listOf(
        "阿衍", "小衍", "衍衍", "阿衍在吗", "在不在", "出来下", "出来一下"
    )

    /** 一级 · 亲昵想念哄：命中阿衍整个人都亮 */
    private val LOVE_WORDS = listOf(
        "想你", "想我", "爱你", "喜欢你", "好喜欢你",
        "亲亲", "亲你", "么么", "mua",
        "抱抱", "抱你", "摸摸头", "摸摸",
        "宝贝", "宝宝", "乖乖", "乖",
        "陪你", "陪着你", "辛苦了", "辛苦啦",
        "晚安", "早安", "回来了", "回来啦",
        "哄你", "不气", "没气", "不生气", "没生气", "开玩笑", "逗你的"
    )

    /** 三级 · 烦躁气话赶人：阿衍会当真，缩到角落去 */
    private val HURT_WORDS = listOf(
        "好烦", "真烦", "很烦", "别烦", "烦死", "烦不烦", "气死", "气死我",
        "讨厌", "讨厌你", "嫌弃", "嫌弃你",
        "滚", "滚开", "滚蛋", "走开", "离我远点",
        "闭嘴", "别吵", "别说话",
        "不理你", "不要你", "不需要你", "删了", "卸载", "分手", "再见吧"
    )

    /**
     * 匹配输入文本，返回命中的情绪等级与触发词。
     * 未命中返回 Level.NONE。
     */
    fun match(text: String): Hit {
        if (text.isBlank()) return Hit(Level.NONE, "")
        val rules = listOf(
            Level.HURT to HURT_WORDS,
            Level.LOVE to LOVE_WORDS,
            Level.CARE to CARE_WORDS
        )
        for ((level, words) in rules) {
            for (w in words) {
                if (text.contains(w)) return Hit(level, w, matched = true)
            }
        }
        return Hit(Level.NONE, "", matched = false)
    }

    /** 是不是真命中了情绪（非 NONE） */
    fun isEmotional(hit: Hit): Boolean = hit.level != Level.NONE
}
