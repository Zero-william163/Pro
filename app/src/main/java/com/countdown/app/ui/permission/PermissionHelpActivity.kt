package com.countdown.app.ui.permission

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.countdown.app.ui.theme.CountdownTheme
import com.countdown.app.util.PermissionChecker
import com.countdown.app.util.PermissionGuideData

/**
 * 权限帮助中心
 *
 * 按手机品牌展示各权限的详细分步开启指引，帮助用户在无法直达系统权限页面时手动完成设置。
 *
 * 设计要点：
 * 1. Material Design 3 卡片式布局，与权限中心页面风格保持一致
 * 2. 自动检测当前设备品牌并高亮标记（「当前设备」徽章）
 * 3. 两种视图：
 *    - 品牌列表视图：以网格展示全部 12 个品牌，点击进入对应指引
 *    - 品牌详情视图：列出该品牌下所有权限的分步操作指引
 * 4. 每项权限展示：为什么需要 / 不开启的后果 / 编号步骤（步骤间以 ↓ 连接）/ 注意事项
 */
class PermissionHelpActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PermissionHelpActivity::class.java))
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CountdownTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionHelpScreen(onBack = { finish() })
                }
            }
        }
    }
}

// ==================== 颜色定义 ====================

/** 注意事项 - 琥珀色 */
private val NotesBgColor = Color(0xFFFFF8E1)
private val NotesColor = Color(0xFFE65100)
private val NotesTextColor = Color(0xFF5D4037)

/** 不开启的后果 - 红色 */
private val ConsequenceColor = Color(0xFFEF4444)
private val ConsequenceBgColor = Color(0xFFFFEBEE)

// ==================== 主屏幕 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionHelpScreen(onBack: () -> Unit) {
    // 自动检测当前设备品牌，并匹配到支持的品牌列表
    val detectedBrand = remember { PermissionChecker.getDeviceBrand() }
    val matchedBrand = remember(detectedBrand) { matchDetectedBrand(detectedBrand) }

    // null 表示处于品牌列表视图，非 null 表示处于品牌详情视图
    var selectedBrand by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedBrand?.let { "$it 权限设置指引" } ?: "权限帮助",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // 详情视图：返回品牌列表；列表视图：退出页面
                        if (selectedBrand != null) selectedBrand = null else onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        val brand = selectedBrand
        if (brand == null) {
            BrandListContent(
                paddingValues = paddingValues,
                matchedBrand = matchedBrand,
                onBrandSelected = { selectedBrand = it }
            )
        } else {
            BrandDetailContent(
                paddingValues = paddingValues,
                brandName = brand,
                onBack = { selectedBrand = null }
            )
        }
    }
}

// ==================== 品牌匹配 ====================

/**
 * 将 [PermissionChecker.getDeviceBrand] 返回的品牌名称匹配到
 * [PermissionGuideData.ALL_BRANDS] 中的标准品牌名。
 *
 * 例如检测到的 "Pixel" 会被映射为 "Google Pixel"。
 * 匹配不到时返回 null（此时不高亮任何品牌）。
 */
private fun matchDetectedBrand(detected: String): String? {
    // 1. 大小写无关的精确匹配
    PermissionGuideData.ALL_BRANDS.firstOrNull { it.equals(detected, ignoreCase = true) }
        ?.let { return it }

    // 2. 已知别名 / 子串匹配（兼容 getDeviceBrand 的 else 分支返回值）
    return when {
        detected.contains("google", ignoreCase = true) -> PermissionGuideData.BRAND_PIXEL
        detected.contains("pixel", ignoreCase = true) -> PermissionGuideData.BRAND_PIXEL
        detected.contains("honor", ignoreCase = true) -> PermissionGuideData.BRAND_HONOR
        detected.contains("huawei", ignoreCase = true) -> PermissionGuideData.BRAND_HUAWEI
        detected.contains("redmi", ignoreCase = true) -> PermissionGuideData.BRAND_REDMI
        detected.contains("xiaomi", ignoreCase = true) -> PermissionGuideData.BRAND_XIAOMI
        detected.contains("realme", ignoreCase = true) -> PermissionGuideData.BRAND_REALME
        detected.contains("oppo", ignoreCase = true) -> PermissionGuideData.BRAND_OPPO
        detected.contains("iqoo", ignoreCase = true) -> PermissionGuideData.BRAND_IQOO
        detected.contains("vivo", ignoreCase = true) -> PermissionGuideData.BRAND_VIVO
        detected.contains("samsung", ignoreCase = true) -> PermissionGuideData.BRAND_SAMSUNG
        detected.contains("oneplus", ignoreCase = true) -> PermissionGuideData.BRAND_ONEPLUS
        detected.contains("motorola", ignoreCase = true) -> PermissionGuideData.BRAND_MOTOROLA
        else -> null
    }
}

// ==================== 品牌列表视图 ====================

@Composable
fun BrandListContent(
    paddingValues: PaddingValues,
    matchedBrand: String?,
    onBrandSelected: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部说明卡片（横跨两列）
        item(span = { GridItemSpan(maxLineSpan) }) {
            HelpIntroCard(matchedBrand = matchedBrand)
        }

        // 区域标题
        item(span = { GridItemSpan(maxLineSpan) }) {
            HelpSectionTitle(text = "选择您的手机品牌")
        }

        // 品牌卡片
        items(PermissionGuideData.ALL_BRANDS) { brand ->
            BrandCard(
                brandName = brand,
                isCurrentDevice = brand == matchedBrand,
                onClick = { onBrandSelected(brand) }
            )
        }

        // 底部提示
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "未找到您的品牌？可选择系统最接近的品牌，或参考对应权限的「通用指引」。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}

/** 帮助中心顶部说明卡片 */
@Composable
fun HelpIntroCard(matchedBrand: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "权限帮助中心",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (matchedBrand != null) {
                        "已检测到您的设备为「$matchedBrand」，点击下方对应品牌查看详细开启步骤。"
                    } else {
                        "这里收录了各品牌手机开启权限的详细步骤，选择您的品牌即可查看对应指引。"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/** 区域标题 */
@Composable
fun HelpSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, top = 4.dp)
    )
}

/** 单个品牌卡片 */
@Composable
fun BrandCard(
    brandName: String,
    isCurrentDevice: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isCurrentDevice) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isCurrentDevice) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentDevice) 4.dp else 2.dp
        ),
        border = if (isCurrentDevice) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = brandName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isCurrentDevice) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "当前设备",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                Text(
                    text = "查看设置指引 ›",
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ==================== 品牌详情视图 ====================

@Composable
fun BrandDetailContent(
    paddingValues: PaddingValues,
    brandName: String,
    onBack: () -> Unit
) {
    val guides = remember(brandName) {
        PermissionGuideData.getGuidesForBrand(brandName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        BrandDetailIntroCard(brandName = brandName, permissionCount = guides.size)

        Spacer(modifier = Modifier.height(16.dp))

        guides.forEach { (guide, brandGuide) ->
            val isBrandSpecific = guide.brandGuides.containsKey(brandName)
            PermissionGuideCard(
                guide = guide,
                brandGuide = brandGuide,
                isBrandSpecific = isBrandSpecific
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))
        BackToBrandListButton(onClick = onBack)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** 品牌详情顶部说明卡片 */
@Composable
fun BrandDetailIntroCard(brandName: String, permissionCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "$brandName 设备权限指引",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "共 $permissionCount 项权限，请按以下步骤逐一设置，以确保提醒功能正常工作。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ==================== 权限引导卡片（核心组件） ====================

@Composable
fun PermissionGuideCard(
    guide: PermissionGuideData.PermissionGuide,
    brandGuide: PermissionGuideData.BrandGuide?,
    isBrandSpecific: Boolean
) {
    val steps = brandGuide?.steps ?: emptyList()
    val notes = brandGuide?.notes

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ===== 标题行：权限名称 + 来源徽章 =====
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = guide.permissionName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isBrandSpecific) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isBrandSpecific) "品牌专属" else "通用指引",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBrandSpecific) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ===== 为什么需要 =====
            GuideInfoSection(
                icon = Icons.Default.Info,
                iconTint = MaterialTheme.colorScheme.primary,
                label = "为什么需要",
                content = guide.whyRequired,
                background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // ===== 不开启的后果 =====
            GuideInfoSection(
                icon = Icons.Default.Warning,
                iconTint = ConsequenceColor,
                label = "不开启的后果",
                content = guide.consequenceIfDisabled,
                background = ConsequenceBgColor
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ===== 操作步骤 =====
            Text(
                text = "操作步骤",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (steps.isEmpty()) {
                Text(
                    text = "暂无分步指引，请参考上方通用说明。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                steps.forEachIndexed { index, step ->
                    GuideStepItem(
                        stepNumber = index + 1,
                        step = step,
                        isLast = index == steps.lastIndex
                    )
                }
            }

            // ===== 注意事项 =====
            if (!notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                GuideNotesBox(notes = notes)
            }
        }
    }
}

// ==================== 单条操作步骤 ====================

@Composable
fun GuideStepItem(
    stepNumber: Int,
    step: PermissionGuideData.GuideStep,
    isLast: Boolean
) {
    Column {
        Row(verticalAlignment = Alignment.Top) {
            // 编号圆圈（使用主品牌色）
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = step.stepText,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                    if (step.iconHint.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = step.iconHint,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 步骤间向下的箭头连接符（↓）
        if (!isLast) {
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==================== 信息区块（为什么需要 / 不开启的后果） ====================

@Composable
fun GuideInfoSection(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    content: String,
    background: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iconTint
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = content,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ==================== 注意事项高亮区块 ====================

@Composable
fun GuideNotesBox(notes: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NotesBgColor)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = NotesColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "注意事项",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NotesColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notes,
                    fontSize = 13.sp,
                    color = NotesTextColor,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ==================== 返回品牌列表按钮 ====================

@Composable
fun BackToBrandListButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "返回品牌列表")
    }
}
