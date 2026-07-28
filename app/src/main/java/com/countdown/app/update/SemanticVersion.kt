package com.countdown.app.update

/**
 * 语义化版本号 (Semantic Version) 解析与比较工具。
 *
 * 支持 "1.2.3"、"v1.2.3"、"1.2" 等格式的版本号，
 * 按 Major.Minor.Patch 逐段比较，而非字符串比较。
 *
 * 例如: "1.10.0" > "1.9.0" (正确)
 *       "1.10.0".compareTo("1.9.0") < 0 (字符串比较是错误的)
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<SemanticVersion> {

    companion object {

        /**
         * 从字符串解析语义化版本号。
         * 自动去除前缀 "v" 或 "V"。
         * 支持格式: "1.2.3", "v1.2.3", "1.2", "1"
         *
         * @param versionString 原始版本字符串，例如 "v1.1.0"
         * @return 解析后的 SemanticVersion，如果格式无效返回 null
         */
        fun parse(versionString: String): SemanticVersion? {
            if (versionString.isBlank()) return null

            // 去除前缀 v/V 和首尾空格
            val cleaned = versionString.trim().removePrefix("v").removePrefix("V")

            // 去除可能存在的后缀（如 -beta, -rc1）
            val core = cleaned.split("-", "_", "+")[0]

            val parts = core.split(".")
            if (parts.isEmpty()) return null

            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

            if (major < 0 || minor < 0 || patch < 0) return null

            return SemanticVersion(major, minor, patch)
        }
    }

    /**
     * 比较两个语义化版本号。
     *
     * @return 正数表示 this > other，
     *         负数表示 this < other，
     *         0 表示相等
     */
    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    /**
     * 转换为标准字符串表示，例如 "1.1.0"
     */
    override fun toString(): String = "$major.$minor.$patch"
}
