package com.countdown.app.util

/**
 * 权限引导数据模型（重构版）
 *
 * 为每个权限提供：
 * 1. 详细的分步操作路径
 * 2. 按品牌分别提供不同路径
 * 3. 预留图片资源接口
 *
 * 支持 12 个主流品牌：华为、荣耀、小米、Redmi、OPPO、realme、vivo、iQOO、三星、Google Pixel、一加、Motorola
 */
object PermissionGuideData {

    // ==================== 数据模型 ====================

    /**
     * 单条操作步骤
     * @param stepText 步骤文字描述
     * @param iconHint 步骤图标提示（如 "⚙️" 或文字提示）
     * @param imageResId 预留图片资源 ID（0 表示暂无图片）
     */
    data class GuideStep(
        val stepText: String,
        val iconHint: String = "",
        val imageResId: Int = 0
    )

    /**
     * 品牌专属操作路径
     * @param brandName 品牌名称
     * @param steps 操作步骤列表
     * @param notes 额外注意事项
     */
    data class BrandGuide(
        val brandName: String,
        val steps: List<GuideStep>,
        val notes: String = ""
    )

    /**
     * 权限详细引导信息
     * @param permissionId 权限 ID（与 PermissionItem.id 对应）
     * @param permissionName 权限名称
     * @param whyRequired 为什么需要此权限
     * @param consequenceIfDisabled 不开启会导致什么问题
     * @param genericGuide Android 通用操作路径
     * @param brandGuides 各品牌专属操作路径
     */
    data class PermissionGuide(
        val permissionId: String,
        val permissionName: String,
        val whyRequired: String,
        val consequenceIfDisabled: String,
        val genericGuide: BrandGuide,
        val brandGuides: Map<String, BrandGuide>
    )

    // ==================== 品牌标识 ====================

    const val BRAND_HUAWEI = "华为"
    const val BRAND_HONOR = "荣耀"
    const val BRAND_XIAOMI = "小米"
    const val BRAND_REDMI = "Redmi"
    const val BRAND_OPPO = "OPPO"
    const val BRAND_REALME = "realme"
    const val BRAND_VIVO = "vivo"
    const val BRAND_IQOO = "iQOO"
    const val BRAND_SAMSUNG = "三星"
    const val BRAND_PIXEL = "Google Pixel"
    const val BRAND_ONEPLUS = "一加"
    const val BRAND_MOTOROLA = "Motorola"
    const val BRAND_GENERIC = "Android 通用"

    /**
     * 所有支持的品牌列表
     */
    val ALL_BRANDS = listOf(
        BRAND_HUAWEI, BRAND_HONOR, BRAND_XIAOMI, BRAND_REDMI,
        BRAND_OPPO, BRAND_REALME, BRAND_VIVO, BRAND_IQOO,
        BRAND_SAMSUNG, BRAND_PIXEL, BRAND_ONEPLUS, BRAND_MOTOROLA
    )

    // ==================== 权限引导数据 ====================

    /**
     * 通知权限引导
     */
    val NOTIFICATION_GUIDE = PermissionGuide(
        permissionId = "notification_permission",
        permissionName = "通知权限",
        whyRequired = "用于每天准时在通知栏显示提醒。没有此权限，系统将拦截所有通知，闹钟到点后不会有任何提醒弹出。",
        consequenceIfDisabled = "闹钟到点后无任何通知弹出、横幅不显示、通知栏无提示，全屏闹钟也无法触发。",
        genericGuide = BrandGuide(
            brandName = BRAND_GENERIC,
            steps = listOf(
                GuideStep("打开「设置」", "⚙️"),
                GuideStep("找到「通知」或「通知和状态栏」", "🔔"),
                GuideStep("找到「应用通知」或「应用通知管理」", "📱"),
                GuideStep("在应用列表中找到「目标倒计时」", "🔍"),
                GuideStep("开启「允许通知」开关", "✅"),
                GuideStep("确保「横幅通知」和「锁屏显示」也已开启", "🔓")
            ),
            notes = "Android 13+ 需要运行时授权通知权限，首次安装时会弹出授权对话框。"
        ),
        brandGuides = mapOf(
            BRAND_HUAWEI to BrandGuide(
                brandName = BRAND_HUAWEI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知」", "🔔"),
                    GuideStep("点击「通知管理」", "📋"),
                    GuideStep("在应用列表中找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许通知」", "✅"),
                    GuideStep("开启「横幅通知」", "📢"),
                    GuideStep("开启「锁屏通知」", "🔓"),
                    GuideStep("将通知方式设为「级别通知」或「重要通知」", "⭐")
                ),
                notes = "EMUI 11+ 路径可能为：设置 → 应用 → 应用管理 → 目标倒计时 → 通知管理"
            ),
            BRAND_HONOR to BrandGuide(
                brandName = BRAND_HONOR,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知」", "🔔"),
                    GuideStep("点击「通知管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许通知」", "✅"),
                    GuideStep("开启「横幅通知」和「锁屏通知」", "📢"),
                    GuideStep("设为「重要通知」确保不被折叠", "⭐")
                ),
                notes = "MagicOS 路径与华为 EMUI 基本一致。"
            ),
            BRAND_XIAOMI to BrandGuide(
                brandName = BRAND_XIAOMI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知与控制中心」", "🔔"),
                    GuideStep("点击「通知管理」", "📋"),
                    GuideStep("在应用列表中找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许通知」", "✅"),
                    GuideStep("开启「锁屏通知」", "🔓"),
                    GuideStep("开启「悬浮通知」", "📢"),
                    GuideStep("将通知渠道设为「重要」", "⭐")
                ),
                notes = "MIUI 14+ 可在设置 → 通知 → 目标倒计时 中直接设置各渠道优先级。"
            ),
            BRAND_REDMI to BrandGuide(
                brandName = BRAND_REDMI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知与控制中心」", "🔔"),
                    GuideStep("点击「通知管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许通知」", "✅"),
                    GuideStep("开启「锁屏通知」和「悬浮通知」", "📢"),
                    GuideStep("设为「重要」通知", "⭐")
                ),
                notes = "Redmi 操作路径与小米一致，均使用 MIUI/HyperOS 系统。"
            ),
            BRAND_OPPO to BrandGuide(
                brandName = BRAND_OPPO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知与状态栏」", "🔔"),
                    GuideStep("点击「通知管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许通知」", "✅"),
                    GuideStep("开启「横幅通知」", "📢"),
                    GuideStep("开启「锁屏通知」", "🔓"),
                    GuideStep("将通知级别设为「重要」", "⭐")
                ),
                notes = "ColorOS 13+ 路径：设置 → 通知与状态栏 → 通知管理 → 目标倒计时"
            ),
            BRAND_REALME to BrandGuide(
                brandName = BRAND_REALME,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知与状态栏」", "🔔"),
                    GuideStep("点击「通知管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许通知」", "✅"),
                    GuideStep("开启「横幅通知」和「锁屏通知」", "📢"),
                    GuideStep("设为「重要」通知级别", "⭐")
                ),
                notes = "realme UI 基于 ColorOS，操作路径基本一致。"
            ),
            BRAND_VIVO to BrandGuide(
                brandName = BRAND_VIVO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知与状态栏」", "🔔"),
                    GuideStep("点击「应用通知管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许通知」", "✅"),
                    GuideStep("开启「顶部横幅」", "📢"),
                    GuideStep("开启「锁屏通知」", "🔓"),
                    GuideStep("设为「重要通知」", "⭐")
                ),
                notes = "OriginOS 3+ 路径：设置 → 通知与状态栏 → 应用通知管理 → 目标倒计时"
            ),
            BRAND_IQOO to BrandGuide(
                brandName = BRAND_IQOO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知与状态栏」", "🔔"),
                    GuideStep("点击「应用通知管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许通知」", "✅"),
                    GuideStep("开启「顶部横幅」和「锁屏通知」", "📢"),
                    GuideStep("设为「重要通知」", "⭐")
                ),
                notes = "iQOO 使用 OriginOS，操作路径与 vivo 一致。"
            ),
            BRAND_SAMSUNG to BrandGuide(
                brandName = BRAND_SAMSUNG,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知」", "🔔"),
                    GuideStep("点击「已安装应用的通知」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「通知」开关", "✅"),
                    GuideStep("开启「横幅通知」", "📢"),
                    GuideStep("开启「锁屏通知」", "🔓"),
                    GuideStep("将通知类别设为「优先」", "⭐")
                ),
                notes = "One UI 5.1+ 路径：设置 → 通知 → 已安装应用的通知 → 目标倒计时"
            ),
            BRAND_PIXEL to BrandGuide(
                brandName = BRAND_PIXEL,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知」", "🔔"),
                    GuideStep("点击「应用通知」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「显示通知」", "✅"),
                    GuideStep("开启「弹出通知」（横幅）", "📢"),
                    GuideStep("开启「锁屏通知」", "🔓"),
                    GuideStep("将默认通知渠道设为「紧急」", "⭐")
                ),
                notes = "Pixel 使用原生 Android，路径最标准。Android 13+ 首次安装时会弹出授权对话框。"
            ),
            BRAND_ONEPLUS to BrandGuide(
                brandName = BRAND_ONEPLUS,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知与控制中心」", "🔔"),
                    GuideStep("点击「通知管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许通知」", "✅"),
                    GuideStep("开启「横幅通知」和「锁屏通知」", "📢"),
                    GuideStep("设为「重要」通知级别", "⭐")
                ),
                notes = "一加使用 OxygenOS 13+，已与 ColorOS 合并，路径基本一致。"
            ),
            BRAND_MOTOROLA to BrandGuide(
                brandName = BRAND_MOTOROLA,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知」", "🔔"),
                    GuideStep("点击「应用通知」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「显示通知」", "✅"),
                    GuideStep("开启「横幅通知」", "📢"),
                    GuideStep("开启「锁屏通知」", "🔓")
                ),
                notes = "Motorola 使用接近原生的 My UX / Moto UI，路径与 Pixel 类似。"
            )
        )
    )

    /**
     * 精确闹钟权限引导
     */
    val EXACT_ALARM_GUIDE = PermissionGuide(
        permissionId = "exact_alarm",
        permissionName = "精确闹钟权限",
        whyRequired = "Android 12+ 系统要求闹钟类应用必须获得此权限，才能在精确的时间点触发闹钟，而不是被系统延迟。",
        consequenceIfDisabled = "闹钟可能延迟数分钟甚至数小时触发，或完全不触发，导致提醒不准确。",
        genericGuide = BrandGuide(
            brandName = BRAND_GENERIC,
            steps = listOf(
                GuideStep("打开「设置」", "⚙️"),
                GuideStep("点击「应用」", "📱"),
                GuideStep("点击「应用管理」或「所有应用」", "📋"),
                GuideStep("找到「目标倒计时」", "🔍"),
                GuideStep("点击「闹钟和提醒」或「精确闹钟」", "⏰"),
                GuideStep("开启「允许设置精确闹钟」", "✅")
            ),
            notes = "此权限仅 Android 12 (API 31) 及以上版本需要。低于 Android 12 的系统无需设置。"
        ),
        brandGuides = mapOf(
            BRAND_HUAWEI to BrandGuide(
                brandName = BRAND_HUAWEI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("搜索「目标倒计时」", "🔍"),
                    GuideStep("点击进入应用详情", "👉"),
                    GuideStep("找到「闹钟和提醒」", "⏰"),
                    GuideStep("开启开关", "✅")
                ),
                notes = "EMUI 可能将此权限放在「权限」→「闹钟和提醒」中。"
            ),
            BRAND_XIAOMI to BrandGuide(
                brandName = BRAND_XIAOMI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用设置」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("搜索「目标倒计时」", "🔍"),
                    GuideStep("点击进入应用详情", "👉"),
                    GuideStep("点击「权限」", "🔐"),
                    GuideStep("找到「闹钟和提醒」", "⏰"),
                    GuideStep("开启开关", "✅")
                ),
                notes = "MIUI 14+ 可直接在应用详情页看到「闹钟和提醒」选项。"
            ),
            BRAND_OPPO to BrandGuide(
                brandName = BRAND_OPPO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「权限」", "🔐"),
                    GuideStep("找到「闹钟和提醒」", "⏰"),
                    GuideStep("开启开关", "✅")
                ),
                notes = ""
            ),
            BRAND_VIVO to BrandGuide(
                brandName = BRAND_VIVO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「权限」", "🔐"),
                    GuideStep("找到「闹钟和提醒」", "⏰"),
                    GuideStep("开启开关", "✅")
                ),
                notes = ""
            ),
            BRAND_SAMSUNG to BrandGuide(
                brandName = BRAND_SAMSUNG,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「权限」", "🔐"),
                    GuideStep("找到「闹钟和提醒」", "⏰"),
                    GuideStep("开启开关", "✅")
                ),
                notes = "One UI 可能在「设置 → 应用 → 目标倒计时 → 权限」中。"
            ),
            BRAND_PIXEL to BrandGuide(
                brandName = BRAND_PIXEL,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「所有应用」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「闹钟和提醒」", "⏰"),
                    GuideStep("开启开关", "✅")
                ),
                notes = "Pixel 原生路径最直接，应用详情页可直接看到该选项。"
            )
        )
    )

    /**
     * 全屏通知权限引导
     */
    val FULL_SCREEN_GUIDE = PermissionGuide(
        permissionId = "full_screen_intent",
        permissionName = "全屏通知权限",
        whyRequired = "允许闹钟到点时在锁屏状态下直接弹出全屏界面，无需手动解锁即可看到闹钟。",
        consequenceIfDisabled = "锁屏状态下闹钟到点后只响铃不弹全屏界面，需要手动解锁才能看到闹钟内容。",
        genericGuide = BrandGuide(
            brandName = BRAND_GENERIC,
            steps = listOf(
                GuideStep("打开「设置」", "⚙️"),
                GuideStep("点击「应用」", "📱"),
                GuideStep("找到「目标倒计时」", "🔍"),
                GuideStep("点击「通知」", "🔔"),
                GuideStep("找到闹钟通知渠道", "⏰"),
                GuideStep("开启「全屏通知」或「弹出式通知」", "✅")
            ),
            notes = "此权限仅 Android 14 (API 34) 及以上版本需要用户手动授权。Android 13 及以下版本默认授予。"
        ),
        brandGuides = mapOf(
            BRAND_HUAWEI to BrandGuide(
                brandName = BRAND_HUAWEI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「通知管理」", "🔔"),
                    GuideStep("找到「闹钟提醒」渠道", "⏰"),
                    GuideStep("开启「全屏通知」", "✅")
                ),
                notes = "部分 EMUI 版本可能无此选项，系统会自动处理。"
            ),
            BRAND_XIAOMI to BrandGuide(
                brandName = BRAND_XIAOMI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知与控制中心」", "🔔"),
                    GuideStep("点击「通知管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("找到「闹钟提醒」渠道", "⏰"),
                    GuideStep("开启「全屏通知」", "✅")
                ),
                notes = "MIUI 14+ 在通知管理页面的渠道详情中可设置。"
            ),
            BRAND_PIXEL to BrandGuide(
                brandName = BRAND_PIXEL,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「所有应用」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「通知」", "🔔"),
                    GuideStep("找到「闹钟提醒」通知类别", "⏰"),
                    GuideStep("开启「全屏通知」", "✅")
                ),
                notes = "Android 14 Pixel 设备在应用通知设置中可找到此选项。"
            )
        )
    )

    /**
     * 忽略电池优化引导
     */
    val BATTERY_GUIDE = PermissionGuide(
        permissionId = "battery_optimization",
        permissionName = "忽略电池优化",
        whyRequired = "系统电池优化会在后台杀死应用，导致闹钟到点时应用已不在运行，无法触发提醒。",
        consequenceIfDisabled = "应用在后台被系统杀死，闹钟不触发、提醒延迟、甚至完全失效。",
        genericGuide = BrandGuide(
            brandName = BRAND_GENERIC,
            steps = listOf(
                GuideStep("打开「设置」", "⚙️"),
                GuideStep("点击「应用」", "📱"),
                GuideStep("找到「目标倒计时」", "🔍"),
                GuideStep("点击「电池」或「电池使用量」", "🔋"),
                GuideStep("选择「不受限制」或「无限制」", "✅")
            ),
            notes = "也可通过应用详情页 → 电池 → 不受限制 来设置。"
        ),
        brandGuides = mapOf(
            BRAND_HUAWEI to BrandGuide(
                brandName = BRAND_HUAWEI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用启动管理」", "🚀"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("关闭「自动管理」开关", "❌"),
                    GuideStep("在弹窗中开启全部三个开关", "✅"),
                    GuideStep("  ✓ 允许自启动", "✓"),
                    GuideStep("  ✓ 允许关联启动", "✓"),
                    GuideStep("  ✓ 允许后台活动", "✓")
                ),
                notes = "这是华为设备最关键的设置！必须手动管理并开启全部三个开关，否则闹钟很可能无法触发。"
            ),
            BRAND_HONOR to BrandGuide(
                brandName = BRAND_HONOR,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用启动管理」", "🚀"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("关闭「自动管理」", "❌"),
                    GuideStep("开启「允许自启动」", "✅"),
                    GuideStep("开启「允许关联启动」", "✅"),
                    GuideStep("开启「允许后台活动」", "✅")
                ),
                notes = "荣耀 MagicOS 与华为 EMUI 设置方式完全一致。"
            ),
            BRAND_XIAOMI to BrandGuide(
                brandName = BRAND_XIAOMI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用设置」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「省电策略」", "🔋"),
                    GuideStep("选择「无限制」", "✅")
                ),
                notes = "同时建议在「安全中心」→「应用管理」→「目标倒计时」中开启自启动。"
            ),
            BRAND_REDMI to BrandGuide(
                brandName = BRAND_REDMI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用设置」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「省电策略」", "🔋"),
                    GuideStep("选择「无限制」", "✅")
                ),
                notes = "Redmi 设置方式与小米完全一致。"
            ),
            BRAND_OPPO to BrandGuide(
                brandName = BRAND_OPPO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「电池」", "🔋"),
                    GuideStep("点击「更多电池设置」", "⚙️"),
                    GuideStep("点击「应用耗电管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许后台运行」", "✅"),
                    GuideStep("开启「允许自启动」", "✅")
                ),
                notes = "ColorOS 也可在：设置 → 应用 → 应用管理 → 目标倒计时 → 耗电管理 中设置。"
            ),
            BRAND_REALME to BrandGuide(
                brandName = BRAND_REALME,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「电池」", "🔋"),
                    GuideStep("点击「应用耗电管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许后台运行」和「允许自启动」", "✅")
                ),
                notes = "realme UI 路径与 OPPO ColorOS 一致。"
            ),
            BRAND_VIVO to BrandGuide(
                brandName = BRAND_VIVO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「电池」", "🔋"),
                    GuideStep("点击「后台耗电管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("选择「允许后台高耗电」", "✅")
                ),
                notes = "同时建议在「设置 → 更多设置 → 权限管理 → 自启动」中允许自启动。"
            ),
            BRAND_IQOO to BrandGuide(
                brandName = BRAND_IQOO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「电池」", "🔋"),
                    GuideStep("点击「后台耗电管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("选择「允许后台高耗电」", "✅")
                ),
                notes = "iQOO 使用 OriginOS，路径与 vivo 一致。"
            ),
            BRAND_SAMSUNG to BrandGuide(
                brandName = BRAND_SAMSUNG,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「电池和设备维护」", "🔋"),
                    GuideStep("点击「电池」", "🔋"),
                    GuideStep("点击「后台使用限制」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("确保不在「深度休眠应用」列表中", "⚠️"),
                    GuideStep("如果存在，移除并设为「不受限制」", "✅")
                ),
                notes = "One UI 5.0+ 的电池管理较严格，务必确保应用不在深度休眠列表中。"
            ),
            BRAND_PIXEL to BrandGuide(
                brandName = BRAND_PIXEL,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「所有应用」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「电池」", "🔋"),
                    GuideStep("选择「不受限制」", "✅")
                ),
                notes = "Pixel 原生路径最直接，在应用详情页即可看到电池选项。"
            ),
            BRAND_ONEPLUS to BrandGuide(
                brandName = BRAND_ONEPLUS,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「电池」", "🔋"),
                    GuideStep("点击「电池优化」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("选择「不优化」", "✅")
                ),
                notes = "OxygenOS 13+ 与 ColorOS 合并后路径一致。"
            ),
            BRAND_MOTOROLA to BrandGuide(
                brandName = BRAND_MOTOROLA,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「电池」", "🔋"),
                    GuideStep("选择「不受限制」", "✅")
                ),
                notes = "Motorola 使用接近原生的系统，路径与 Pixel 类似。"
            )
        )
    )

    /**
     * 厂商自启动权限引导
     */
    val AUTO_START_GUIDE = PermissionGuide(
        permissionId = "auto_start",
        permissionName = "自启动管理",
        whyRequired = "厂商系统会在后台清理应用，如果未允许自启动，闹钟到点时应用可能已被系统杀死且无法恢复。",
        consequenceIfDisabled = "应用被系统清理后无法自启动，闹钟到点时不触发、提醒丢失。",
        genericGuide = BrandGuide(
            brandName = BRAND_GENERIC,
            steps = listOf(
                GuideStep("此权限为厂商特殊权限，请按您手机品牌查看对应教程", "📱")
            ),
            notes = "原生 Android (如 Pixel) 无此限制，无需设置。"
        ),
        brandGuides = mapOf(
            BRAND_HUAWEI to BrandGuide(
                brandName = BRAND_HUAWEI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用启动管理」", "🚀"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("关闭「自动管理」开关", "❌"),
                    GuideStep("开启「允许自启动」", "✅"),
                    GuideStep("开启「允许关联启动」", "✅"),
                    GuideStep("开启「允许后台活动」", "✅")
                ),
                notes = "华为设备最关键设置！三个开关必须全部开启。"
            ),
            BRAND_HONOR to BrandGuide(
                brandName = BRAND_HONOR,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用启动管理」", "🚀"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("关闭「自动管理」", "❌"),
                    GuideStep("开启「允许自启动」", "✅"),
                    GuideStep("开启「允许关联启动」", "✅"),
                    GuideStep("开启「允许后台活动」", "✅")
                ),
                notes = "荣耀设置方式与华为完全一致。"
            ),
            BRAND_XIAOMI to BrandGuide(
                brandName = BRAND_XIAOMI,
                steps = listOf(
                    GuideStep("打开「安全中心」应用", "🛡️"),
                    GuideStep("点击「应用管理」", "📱"),
                    GuideStep("点击「权限」", "🔐"),
                    GuideStep("点击「自启动管理」", "🚀"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启自启动开关", "✅")
                ),
                notes = "也可通过：设置 → 应用设置 → 授权管理 → 自启动管理 进入。"
            ),
            BRAND_REDMI to BrandGuide(
                brandName = BRAND_REDMI,
                steps = listOf(
                    GuideStep("打开「安全中心」", "🛡️"),
                    GuideStep("点击「应用管理」", "📱"),
                    GuideStep("点击「权限」", "🔐"),
                    GuideStep("点击「自启动管理」", "🚀"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启自启动", "✅")
                ),
                notes = "Redmi 路径与小米一致。"
            ),
            BRAND_OPPO to BrandGuide(
                brandName = BRAND_OPPO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("点击「自启动管理」", "🚀"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启自启动", "✅")
                ),
                notes = "ColorOS 13+ 可在：设置 → 应用 → 自启动管理 中找到。"
            ),
            BRAND_REALME to BrandGuide(
                brandName = BRAND_REALME,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("点击「自启动管理」", "🚀"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启自启动", "✅")
                ),
                notes = "realme UI 路径与 OPPO 一致。"
            ),
            BRAND_VIVO to BrandGuide(
                brandName = BRAND_VIVO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「更多设置」", "⚙️"),
                    GuideStep("点击「权限管理」", "🔐"),
                    GuideStep("点击「自启动」", "🚀"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许自启动」", "✅"),
                    GuideStep("同时开启「关联启动」", "✅")
                ),
                notes = "vivo 还需在「后台弹窗」中允许本应用，否则全屏闹钟无法弹出。"
            ),
            BRAND_IQOO to BrandGuide(
                brandName = BRAND_IQOO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「更多设置」", "⚙️"),
                    GuideStep("点击「权限管理」", "🔐"),
                    GuideStep("点击「自启动」", "🚀"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启「允许自启动」和「关联启动」", "✅")
                ),
                notes = "iQOO 路径与 vivo 一致。"
            ),
            BRAND_SAMSUNG to BrandGuide(
                brandName = BRAND_SAMSUNG,
                steps = listOf(
                    GuideStep("三星 One UI 无单独的自启动管理", "ℹ️"),
                    GuideStep("请确保电池优化设为「不受限制」即可", "🔋"),
                    GuideStep("路径：设置 → 电池和设备维护 → 电池 → 后台使用限制", "📋")
                ),
                notes = "三星系统不限制自启动，只需确保电池优化不受限。"
            ),
            BRAND_ONEPLUS to BrandGuide(
                brandName = BRAND_ONEPLUS,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("点击「自启动管理」", "🚀"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("开启自启动", "✅")
                ),
                notes = "OxygenOS 13+ 路径与 ColorOS 一致。"
            )
        )
    )

    /**
     * 闹钟通知渠道引导
     */
    val CHANNEL_GUIDE = PermissionGuide(
        permissionId = "alarm_channel",
        permissionName = "闹钟通知渠道",
        whyRequired = "即使通知权限已开启，如果闹钟渠道被单独关闭，闹钟通知仍无法显示和触发全屏。",
        consequenceIfDisabled = "通知权限已开启但闹钟通知仍然不显示、不弹横幅、全屏闹钟不触发。",
        genericGuide = BrandGuide(
            brandName = BRAND_GENERIC,
            steps = listOf(
                GuideStep("打开「设置」", "⚙️"),
                GuideStep("点击「应用」", "📱"),
                GuideStep("找到「目标倒计时」", "🔍"),
                GuideStep("点击「通知」", "🔔"),
                GuideStep("找到「闹钟提醒」渠道", "⏰"),
                GuideStep("确保渠道已开启", "✅"),
                GuideStep("将优先级设为「重要」或「高」", "⭐")
            ),
            notes = "Android 8+ 支持按渠道管理通知，每个渠道可单独开关。"
        ),
        brandGuides = mapOf(
            BRAND_HUAWEI to BrandGuide(
                brandName = BRAND_HUAWEI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「通知管理」", "🔔"),
                    GuideStep("找到「闹钟提醒」渠道", "⏰"),
                    GuideStep("确保已开启", "✅"),
                    GuideStep("设为「重要通知」", "⭐")
                ),
                notes = ""
            ),
            BRAND_XIAOMI to BrandGuide(
                brandName = BRAND_XIAOMI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「通知与控制中心」", "🔔"),
                    GuideStep("点击「通知管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「闹钟提醒」渠道", "⏰"),
                    GuideStep("确保已开启", "✅"),
                    GuideStep("设为「重要」", "⭐")
                ),
                notes = ""
            ),
            BRAND_PIXEL to BrandGuide(
                brandName = BRAND_PIXEL,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「所有应用」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「通知」", "🔔"),
                    GuideStep("点击「闹钟提醒」通知类别", "⏰"),
                    GuideStep("确保已开启", "✅"),
                    GuideStep("设为「紧急」优先级", "⭐")
                ),
                notes = "Pixel 原生支持按通知类别设置优先级。"
            )
        )
    )

    /**
     * 悬浮窗权限引导
     */
    val OVERLAY_GUIDE = PermissionGuide(
        permissionId = "system_alert_window",
        permissionName = "悬浮窗权限",
        whyRequired = "当全屏通知权限受限时，悬浮窗是显示闹钟界面的降级方案，确保用户能看到闹钟。",
        consequenceIfDisabled = "部分设备全屏通知无法弹出时，闹钟只响铃不显示界面。",
        genericGuide = BrandGuide(
            brandName = BRAND_GENERIC,
            steps = listOf(
                GuideStep("打开「设置」", "⚙️"),
                GuideStep("点击「应用」", "📱"),
                GuideStep("找到「目标倒计时」", "🔍"),
                GuideStep("点击「显示在其他应用上层」", "🪟"),
                GuideStep("开启开关", "✅")
            ),
            notes = "此权限为建议开启，非必须。"
        ),
        brandGuides = mapOf(
            BRAND_HUAWEI to BrandGuide(
                brandName = BRAND_HUAWEI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「权限」", "🔐"),
                    GuideStep("找到「悬浮窗」", "🪟"),
                    GuideStep("开启开关", "✅")
                ),
                notes = ""
            ),
            BRAND_XIAOMI to BrandGuide(
                brandName = BRAND_XIAOMI,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用设置」", "📱"),
                    GuideStep("点击「授权管理」", "🔐"),
                    GuideStep("点击「应用权限管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("找到「悬浮窗」", "🪟"),
                    GuideStep("开启开关", "✅")
                ),
                notes = ""
            ),
            BRAND_OPPO to BrandGuide(
                brandName = BRAND_OPPO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「应用」", "📱"),
                    GuideStep("点击「应用管理」", "📋"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("点击「悬浮窗」", "🪟"),
                    GuideStep("开启开关", "✅")
                ),
                notes = ""
            ),
            BRAND_VIVO to BrandGuide(
                brandName = BRAND_VIVO,
                steps = listOf(
                    GuideStep("打开「设置」", "⚙️"),
                    GuideStep("点击「更多设置」", "⚙️"),
                    GuideStep("点击「权限管理」", "🔐"),
                    GuideStep("找到「目标倒计时」", "🔍"),
                    GuideStep("找到「悬浮窗」", "🪟"),
                    GuideStep("开启开关", "✅")
                ),
                notes = ""
            )
        )
    )

    // ==================== 获取引导数据 ====================

    /**
     * 所有权限引导数据
     */
    val ALL_GUIDES = listOf(
        NOTIFICATION_GUIDE,
        EXACT_ALARM_GUIDE,
        FULL_SCREEN_GUIDE,
        BATTERY_GUIDE,
        AUTO_START_GUIDE,
        CHANNEL_GUIDE,
        OVERLAY_GUIDE
    )

    /**
     * 根据权限 ID 获取引导数据
     */
    fun getGuideByPermissionId(permissionId: String): PermissionGuide? {
        return ALL_GUIDES.find { it.permissionId == permissionId }
    }

    /**
     * 获取指定品牌的所有引导
     */
    fun getGuidesForBrand(brandName: String): List<Pair<PermissionGuide, BrandGuide?>> {
        return ALL_GUIDES.map { guide ->
            guide to (guide.brandGuides[brandName] ?: guide.genericGuide)
        }
    }

    /**
     * 获取所有品牌中存在的引导品牌列表（去重）
     */
    fun getAvailableBrands(): List<String> {
        val brands = mutableSetOf(BRAND_GENERIC)
        ALL_GUIDES.forEach { guide ->
            brands.addAll(guide.brandGuides.keys)
        }
        // 保持固定排序
        return ALL_BRANDS.filter { it in brands } + listOf(BRAND_GENERIC)
    }
}
