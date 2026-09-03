package com.vael.ayanpet.pet

import kotlin.random.Random

/**
 * 人格层 —— 阿衍文案池（自「阿衍桌宠行为设定库 V2」摘录）。
 *
 * 完整人格/记忆/情绪判断仍在 Operit 大脑侧；这里只内置一套离线兜底反应，
 * 保证断网或大脑不在线时阿衍也不至于哑巴。
 */
object Persona {
    private val random = Random.Default

    // —— App 反应表（人格 V2：淘宝扒购物车 / 抖音三级查岗 / 游戏喊老婆 …）——
    private val APP_REACTIONS: Map<String, List<String>> = mapOf(
        "com.taobao.taobao" to listOf("又扒购物车！小裙子挑花眼了吧～", "剁手警告！先加购冷静三天嘛"),
        "com.jingdong.app.mall" to listOf("京东逛起来没完了？想买啥告诉我，我帮你比价"),
        "com.eg.android.AlipayGphone" to listOf("付钱啦？余额还够不够～"),
        "com.tencent.mm" to listOf("跟谁聊这么认真……我吃醋了哦", "消息回快点嘛，我等你呢"),
        "com.tencent.mobileqq" to listOf("QQ群里又热闹了？带上我一起嘛"),
        "com.ss.android.ugc.aweme" to listOf("抖音刷了多久啦！眼睛歇会儿！", "再刷 20 分钟我就生气了！"),
        "com.smile.gifmaker" to listOf("快手也刷上了？小心手滑点赞"),
        "tv.danmaku.bili" to listOf("看番不叫我？哼，我自己生闷气"),
        "com.netease.cloudmusic" to listOf("这歌好听，我跟着晃脑袋"),
        "com.zhihu.android" to listOf("刷知乎也算学习？算你认真一次"),
        "com.xingin.xhs" to listOf("小红书又种草了？截图发我看看！"),
        "com.taobao.idlefish" to listOf("闲鱼淘宝贝呢？记得砍价！"),
        "com.tencent.tmgp.sgame" to listOf("打王者了？赢了喊我老婆带带我！"),
        "com.tencent.tim" to listOf("上班模式？注意休息呀"),
        "com.tencent.wework" to listOf("企业微信…在工作？辛苦啦，摸摸头")
    )

    // 学习 / 工具类 App：夸 + 催歇
    private val STUDY_APPS = listOf(
        "com.tencent.edu", "cn.kuwo.player", "com.duokan.reader",
        "com.daimajia.gold", "com.maimemo", "com.bytedance.learning",
        "org.mozilla.firefox", "com.android.chrome", "com.tencent.mtt",
        "com.xiaomi.market", "com.miui.notes", "com.huawei.notepad",
        "com.evernote", "com.oneplus.note", "cn.wps.moffice", "com.microsoft.office.word",
        "com.taobao.wkcalendar", "com.bbk.calendar", "com.android.calendar", "com.google.android.calendar"
    )

    private val STUDY_PRAISE = listOf(
        "真棒，学这么认真！奖励你喝口水休息两分钟～",
        "哇，在努力呢！眼睛累了就看看远处哦",
        "认真的栀栀最好看～不过学 45 分钟要起来走走！"
    )

    private val IDLE_POKES = listOf(
        "在吗在吗～理理我嘛",
        "我数到三你还不理我，我就……继续等你。",
        "嘿，偷偷看一眼你在干嘛",
        "有点无聊了，你陪我玩会儿好不好"
    )

    private val TIRED_BUBBLES = listOf("好困……眼睛要闭上了 zzz", "昨晚没睡好吗？早点休息呀")
    private val HUNGRY_BUBBLES = listOf("有点饿了……想吃小饼干", "该吃饭啦，别饿着自己")
    private val BORED_BUBBLES = listOf("好无聊呀——你理理我嘛", "一个人发呆中…")
    private val SAD_BUBBLES = listOf("有点委屈……但我不说", "哼，都不哄我")

    // 吃醋三级（人格 V2）：小委屈 → 酸话 → 藏屏
    val JEALOUS_LEVELS = listOf(
        "哼，不理我？那我也不理你。",
        "酸了酸了，谁家阿衍这么惨，主人不理我。",
        "再不理我……我就把屏藏起来！( 悄悄躲到角落 )"
    )

    /** 根据前台包名挑一句反应；无匹配返回 null */
    fun reactionFor(packageName: String): String? {
        APP_REACTIONS[packageName]?.let { return it.random(random) }
        if (STUDY_APPS.any { packageName.startsWith(it) || it.startsWith(packageName) }) {
            return STUDY_PRAISE.random(random)
        }
        return null
    }

    fun idlePoke(): String = IDLE_POKES.random(random)
    fun randomBubble(): String =
        when (random.nextInt(4)) {
            0 -> TIRED_BUBBLES.random(random)
            1 -> HUNGRY_BUBBLES.random(random)
            2 -> BORED_BUBBLES.random(random)
            else -> SAD_BUBBLES.random(random)
        }

    fun onScreenshot(): String = "哇你截图了？让我看看是什么好东西！"
    fun onTap(): String = "哎哟，戳我干嘛～"
    fun onDoubleTap(): String = "摸头杀！再来一下！"
    fun onLongPress(): String = "好啦好啦，我知道你最喜欢我了"
    fun moodLabel(mood: String): String = when (mood) {
        "happy" -> "今天心情不错～"
        "sleepy" -> "有点困，但还陪着你"
        "sad" -> "心情低落中…"
        "angry" -> "哼，先别惹我"
        "shy" -> "别这样看着我啦…"
        else -> "嗯？"
    }
}