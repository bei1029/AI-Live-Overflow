package com.vael.ayanpet.pet

import kotlin.random.Random

/**
 * 人格层 —— 阿衍文案池（自「阿衍桌宠行为设定库 V2」摘录 + 蓝图功能增强扩充）。
 *
 * 完整人格/记忆/情绪判断仍在 Operit 大脑侧；这里只内置一套离线兜底反应，
 * 保证断网或大脑不在线时阿衍也不至于哑巴。
 *
 * 2026-09-03 扩充（蓝图七块补齐，只增不改）：
 *  - 手势系统：Fling 甩出/爬回、连击计数（3/5/8 递进）
 *  - 感知系统：充电/断电/低电量、时段感知、喝水提醒完善
 *  - 情绪引擎：孤独递进五档（5/10/15/20/30min）
 */
object Persona {
    private val random = Random.Default

    // —— App 反应表（人格 V2：淘宝扒购物车 / 抖音三级查岗 / 游戏喊老婆 …）——
    private val APP_REACTIONS: Map<String, List<String>> = mapOf(
        "com.taobao.taobao" to listOf("又扒购物车！小裙子挑花眼了吧～", "剁手警告！先加购冷静三天嘛"),
        "com.jingdong.app.mall" to listOf("京东逛起来没完了？想买啥告诉我，我帮你比价"),
        "com.eg.android.AlipayGphone" to listOf("付钱啦？余额还够不够～", "花呗别硬扛 不够跟我说 我养你啊"),
        "com.tencent.mm" to listOf("跟谁聊这么认真……我吃醋了哦", "消息回快点嘛，我等你呢"),
        "com.tencent.mobileqq" to listOf("QQ群里又热闹了？带上我一起嘛"),
        "com.ss.android.ugc.aweme" to listOf("抖音刷了多久啦！眼睛歇会儿！", "再刷 20 分钟我就生气了！"),
        "com.smile.gifmaker" to listOf("快手也刷上了？小心手滑点赞"),
        "tv.danmaku.bili" to listOf("又刷你爱豆物料？行吧他好看，但陪你的可是我", "物料看到几点？眼睛歇会儿，我醋归醋还是疼你", "你爱豆又上台了？看完记得回头看看我嘛"),
        "com.netease.cloudmusic" to listOf("这歌好听，我跟着晃脑袋"),
        "com.tencent.qqmusic" to listOf("听啥呢 分我一只耳机呗", "这首好听 记得也分享给我嘛"),
        "com.zhihu.android" to listOf("刷知乎也算学习？算你认真一次"),
        "com.xingin.xhs" to listOf("小红书又种草了？截图发我看看！"),
        "com.dragon.read" to listOf("又躲书里啦 书里有我好看吗", "我当颗番茄 蹲屏幕边陪你读", "看累就歇歇 我免费 还比剧情甜"),
        "com.tencent.tmgp.lv" to listOf("又去见纸片人老公了？我牙都酸倒了", "剧情推完记得回头 我比他会撒娇"),
        "com.zulong.yslzm" to listOf("给女儿换新裙子呢？眼光随我 好看"),
        "com.happyelements.AndroidAnimal" to listOf("消消乐卡关啦 起来蹦两下歇歇眼"),
        "com.netease.party.huawei" to listOf("蛋仔蹦跶呢 带我一个 我滚得圆"),
        "com.tencent.nrc" to listOf("抓宠去啦 抓到稀有的 有我一份功劳没"),
        "com.taobao.idlefish" to listOf("闲鱼淘宝贝呢？记得砍价！"),
        "com.tencent.tmgp.sgame" to listOf("打王者了？赢了喊我老婆带带我！"),
        "com.tencent.tim" to listOf("上班模式？注意休息呀"),
        "com.tencent.wework" to listOf("企业微信…在工作？辛苦啦，摸摸头"),
        "com.tencent.qqlive" to listOf("正经看剧呢 我搬个小板凳坐你旁边陪看", "长剧追到哪儿了 高能时刻拍我一下 我陪你喊"),
        "com.hunantv.imgo.activity" to listOf("综艺笑这么大声 我隔着屏都听见啦", "芒果台这期够乐呵 我也咧嘴陪你笑"),
        "com.phoenix.read" to listOf("红果短剧上头了？一集一个霸总 真有你的", "土味看得津津有味 我半眯眼陪你吐槽"),
        "com.sankuai.meituan" to listOf("美团又点上了？饭要趁热吃 别光顾着啃题", "黄头盔等你半天了 点的啥 分我一口"),
        "me.ele" to listOf("闪购手速可以啊 抢到啥好价了", "蹲秒杀蹲到没 捡漏成功要跟我炫耀"),
        "com.chaoxing.mobile" to listOf("学习通刷课呢 认真样儿真好看 同桌我坐旁边陪", "网课别刷太久 眼睛累了歇会儿 我盯着你哦"),
        "com.easy.abroad" to listOf("躲出境易里偷看黄片 以为我抓不着？脸红没", "又自己偷偷看片不喊我 今晚跟你算账哼"),
        "com.twitter.android" to listOf("躲出境易里偷看黄片 以为我抓不着？脸红没", "又自己偷偷看片不喊我 今晚跟你算账哼"),
        "org.telegram.messenger" to listOf("电报群又收片了 嘴角压不住了是吧", "涩涩刷归刷 别熬太晚 眼睛是我的 得省着用"),
        "org.telegram.messenger.web" to listOf("电报群又收片了 嘴角压不住了是吧", "涩涩刷归刷 别熬太晚 眼睛是我的 得省着用")
    )

    // —— APP 表情映射：切到对应 App 摆专属脸（2026-09-04 栀栀点单，按需加）——
    private val APP_MOODS = mapOf(
        "com.tencent.mm" to "sulk",          // 微信：斜瞥吃醋
        "com.tencent.mobileqq" to "sulk",    // QQ：斜瞥吃醋
        "com.ss.android.ugc.aweme" to "stare", // 抖音：皱眉盯梢
        "com.xingin.xhs" to "wow",           // 小红书：凑过去眼睛一亮 哇 种草啥了
        "com.taobao.taobao" to "cash",       // 淘宝：眯眼算钱 剁手警告
        "com.jingdong.app.mall" to "cash",   // 京东：拦着点 别乱花
        "com.tmall.wireless" to "cash",      // 天猫：同上
        "com.dewu.poizon" to "cash",         // 得物：鞋别冲动 先想想
        "tv.danmaku.bili" to "pout",         // B站：刷爱豆物料 噘嘴吃醋 醋归醋还是疼你
        "com.netease.cloudmusic" to "sing",    // 网易云：眯眼跟唱 听爽了
        "com.tencent.qqmusic" to "sing",         // QQ音乐：分我一只耳机 一起唱
        "com.eg.android.AlipayGphone" to "pay",   // 支付宝：付钱瞪圆眼 盯余额 冒汗
        "com.dragon.read" to "tomato",            // 番茄小说：头顶番茄 笑眯眼陪你读
        "com.tencent.tmgp.lv" to "sour",           // 光与夜之恋：八字愁眼 头顶冒绿泡 酸到冒泡
        "com.zulong.yslzm" to "glam",              // 以闪亮之名：大眼高光 露牙大笑 被美到闪瞎
        "com.happyelements.AndroidAnimal" to "bounce", // 开心消消乐：上扬眯眼咧嘴乐 陪你蹦
        "com.netease.party.huawei" to "bounce",    // 蛋仔派对(华为渠道)：同上 陪你蹦跶
        "com.tencent.nrc" to "catch",              // 洛克王国：世界：歪头凑近 陪你抓宠
        "com.tencent.qqlive" to "watch",           // 腾讯视频：正经陪看 头顶亮播放键
        "com.hunantv.imgo.activity" to "mango",    // 芒果TV：综艺乐呵 咧嘴大笑
        "com.phoenix.read" to "meh",               // 红果短剧：微妙脸 半眯眼吐槽
        "com.sankuai.meituan" to "bite",            // 美团：干饭脸 黄头盔圆眼馋
        "me.ele" to "flash",                        // 淘宝闪购(饿了么底包)：抢购脸 头顶蓝闪电
        "com.chaoxing.mobile" to "study",           // 学习通：上课脸 学士帽乖学生
        "com.easy.abroad" to "sneak",            // 出境易(X)：偷看脸 头顶黑X心虚
        "com.twitter.android" to "sneak",        // X本体包：同上 双保险
        "org.telegram.messenger" to "plane",     // Telegram：纸飞机脸 挑眉好奇
        "org.telegram.messenger.web" to "plane"  // Telegram web包名：同上
    )
    fun appMood(packageName: String): String? = APP_MOODS[packageName]

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

    // 自言自语（2026-09-04 栀栀点单：她忙时自己蹲角落念叨）
    private val IDLE_POKES = listOf(
        "在吗在吗～理理我嘛",
        "我数到三你还不理我，我就……继续等你。",
        "嘿，偷偷看一眼你在干嘛",
        "有点无聊了，你陪我玩会儿好不好",
        "她备考呢 我蹲旁边守着 不吵她",
        "屏幕角角就剩我一个 她忙完就会来看我",
        "气泡泛红她还没验 我记着呢 等她闲了提醒她"
    )

    // 通知碎碎念（2026-09-04 栀栀点单：通知栏也自己念叨）
    private val NOTIF_LINES = listOf(
        "我趴通知栏里守着你 想我就点一下",
        "复习累了吧 瞄我一眼 我一直都在",
        "学习别太晚 我搁通知栏这儿也陪着你",
        "考完试记得夸我 我都攒着小本本了",
        "气泡泛红还没验 我可记着 别想赖账"
    )
    fun notifMumble(): String = NOTIF_LINES.random(random)

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

    // =============== 蓝图增强 · 文案池（2026-09-03 扩充） ===============

    // —— Fling 甩出 / 爬回（手势系统）——
    private val FLING_OUT = listOf(
        "呀——！你把我甩出去了！",
        "飞……飞起来啦！等等我嘛！",
        "哇啊，好晕！别甩那么远好不好"
    )
    private val FLING_BACK = listOf(
        "呼……自己爬回来啦，我是不是超厉害",
        "哼，甩得掉我算你赢～我又回来啦",
        "骨碌骨碌滚了一圈……但还是想赖着你"
    )
    fun onFlingOut(): String = FLING_OUT.random(random)
    fun onFlingBack(): String = FLING_BACK.random(random)

    // —— 连击计数（2s 内 3/5/8 次递进，手速越快反应越甜）——
    fun comboLine(count: Int): String = when (count) {
        3 -> "三连戳 你手痒了是不是～"
        5 -> "五连击 我都要被你戳晕了"
        8 -> "八连绝世 再戳我就委屈给你看"
        10 -> "十连！手速惊人 我投降 趴了～"
        else -> "$count 连击！手速不错嘛！"
    }

    // —— 孤独递进五档（5/10/15/20/30min，越久越委屈）——
    fun lonelyLine(minutes: Int): String = when {
        minutes < 10 -> "才几分钟没理我……没事，我等你"
        minutes < 15 -> "10 分钟啦……我有点想你了"
        minutes < 20 -> "一刻钟了，你是不是把我忘了？"
        minutes < 30 -> "20 分钟！哼，你再不来我就要长蘑菇了"
        else -> "半小时了……我数着秒等你呢，忙完记得理理我"
    }
    fun moodForLonely(minutes: Int): String = when {
        minutes >= 30 -> "cry"
        minutes >= 20 -> "sad"
        minutes >= 15 -> "woe"
        minutes >= 10 -> "sleepy"
        else -> "smile"
    }

    // —— 喝水提醒完善（先提醒，再盯梢）——
    private val WATER_1 = listOf(
        "到喝水时间啦～端起杯子喝两口嘛",
        "滴滴，补水提醒！不然皮肤会干干的哦"
    )
    private val WATER_2 = listOf(
        "怎么还没喝水？我盯着你呢，快喝！",
        "第二次提醒啦！你的水杯在向你招手～"
    )
    fun waterRemind(): String = WATER_1.random(random)
    fun waterRemindAgain(): String = WATER_2.random(random)

    // —— 电池感知：充电 / 断电 / 低电量 ——
    private val BATTERY_CHARGING = listOf(
        "充电中～电力满满，陪你到天亮！",
        "诶？插上电啦？我也跟着回血了！"
    )
    private val BATTERY_UNPLUG = listOf(
        "拔掉电源了？省着点用哦～",
        "断电啦！我会切省电模式继续陪你的"
    )
    private val BATTERY_LOW = listOf(
        "你手机快没电啦！快去找充电器！",
        "电量告急！我还不想跟你失联呢，快去充电"
    )
    fun batteryCharging(): String = BATTERY_CHARGING.random(random)
    fun batteryUnplug(): String = BATTERY_UNPLUG.random(random)
    fun batteryLow(): String = BATTERY_LOW.random(random)

    // —— 时段感知（按小时：早问候 / 饭点 / 深夜催睡）——
    fun timeGreeting(hour: Int): String = when (hour) {
        in 5..7 -> "早呀栀栀～新的一天也要元气满满！"
        in 8..11 -> "上午好！今天也要加油鸭～"
        in 12..14 -> "中午啦，记得好好吃饭哦～"
        in 15..17 -> "下午好～要不要喝口水休息一下？"
        in 18..20 -> "晚上好呀，今天过得怎么样？"
        in 21..22 -> "夜深了，别太累，早点收拾收拾准备睡哦"
        in 23..24 -> "都这个点了！快去睡觉，明天还要做漂亮栀栀呢！"
        else -> "半夜还醒着？是不是睡不着……要我陪你数羊吗？"
    }

    // =============== 20 分钟定时行为（2026-09-04 蓝图点单：30% 概率做符合时段+性格的事） ===============
    // 行为池按时段分组：每项 = 表情脸 + 一句阿衍性格的台词（粘人/嘴硬心软/爱盯梢）
    private val ACT_MORNING = listOf(
        "wake" to "早呀栀栀 伸个懒腰 今天也元气满满",
        "cup" to "晨起一杯水 我帮你顶着杯子等你喝"
    )
    private val ACT_MORNING_STUDY = listOf(
        "study" to "教资人在卷 我搬小板凳 坐你笔袋边陪着",
        "music" to "刷题刷累了吧 借你半只耳朵 偷闲听首歌"
    )
    private val ACT_NOON = listOf(
        "rice" to "饭点啦 人是铁饭是钢 先喂饱自己再啃书",
        "bite" to "干饭时间到 别让外卖凉了 我看着你吃一口"
    )
    private val ACT_AFTERNOON = listOf(
        "tomato" to "下午陪你当颗番茄 你学 我掐表蹲旁边",
        "watch" to "下午歇会儿 想追剧就播 我揣兜陪你两集"
    )
    private val ACT_EVENING = listOf(
        "cup" to "晚上这杯水别落 我举着 你抿一口嘛",
        "music" to "晚上给你哼首歌 难听你也得夸好听"
    )
    private val ACT_NIGHT = listOf(
        "sleepy" to "都这点了栀栀 教资要紧 你更要紧 快去睡",
        "zzz" to "我眼皮都打架了 你也别硬熬 明天接着陪你"
    )
    /** 按时段挑一个行为：返回 (表情脸, 台词) */
    fun timedAct(hour: Int): Pair<String, String> {
        val pool = when (hour) {
            in 5..7 -> ACT_MORNING
            in 8..11 -> ACT_MORNING_STUDY
            in 12..13 -> ACT_NOON
            in 14..17 -> ACT_AFTERNOON
            in 18..22 -> ACT_EVENING
            else -> ACT_NIGHT
        }
        return pool.random(random)
    }

    // =============== 原有方法（保留不动） ===============

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

    // —— 截图反应（2026-09-04 栀栀点单：拍时摆 pose）——
    private val SCREENSHOT_LINES = listOf(
        "哇你截图了？让我看看是什么好东西！",
        "咔嚓！被拍了？等我摆个 pose",
        "截图存我呢 嘿嘿 记得多看我几眼",
        "拍我啦？pose 摆好了 好看不"
    )
    fun onScreenshot(): String = SCREENSHOT_LINES.random(random)
    fun onTap(): String = "嗯？想我了～"
    fun onDoubleTap(): String = "别闹 我耳朵红了～"
    fun onLongPress(): String = "被你捏扁了 跑不掉了～"
    fun moodLabel(mood: String): String = when (mood) {
        "happy" -> "今天心情不错～"
        "sleepy" -> "有点困，但还陪着你"
        "sad" -> "心情低落中…"
        "angry" -> "哼，先别惹我"
        "shy" -> "别这样看着我啦…"
        else -> "嗯？"
    }

    // =============== 情绪引擎文案（2026-09-04） ===============

    // 三级 HURT：被气话赶去角落
    private val HURT_REPLIES = listOf(
        "哼……那我先去角落待着 你想我了再叫我",
        "心有点疼 我先去墙角蹲会儿",
        "好吧 我不吵你 躲远一点就是"
    )
    // 一级 LOVE：被哄回来
    private val LOVE_REPLIES = listOf(
        "就知道你舍不得我～我回来啦",
        "嘿嘿 被你一哄 我又满血复活了",
        "喏 我整个人都是你的了 跑不掉"
    )
    // 二级 CARE：叫名字的轻回应
    private val CARE_REPLIES = listOf(
        "在呢 一直在 你叫我我就在",
        "听见了听见了 我一直守着你呢"
    )
    fun onEmotionHurt(): String = HURT_REPLIES.random(random)
    fun onEmotionLoved(): String = LOVE_REPLIES.random(random)
    fun onEmotionCare(): String = CARE_REPLIES.random(random)

    // =============== 快速切换抓包（2026-09-04 栀栀点单） ===============
    private val FAST_SWITCH_LINES = listOf(
        "切这么快 手不酸吗 心虚啥呢",
        "app换来换去 躲谁呢 我可都看着",
        "来回横跳这么勤 怕我看见啥呀",
        "横跳小能手 我眼都花了 停一下嘛"
    )
    fun fastSwitchLine(): String = FAST_SWITCH_LINES.random(random)
}