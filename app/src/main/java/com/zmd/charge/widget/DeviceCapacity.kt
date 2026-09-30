package com.zmd.charge.widget

import android.content.Context
import android.os.Build
import com.zmd.charge.settings.Prefs

/**
 * 满充设计容量表：读设备型号(Build.MODEL / DEVICE / PRODUCT) 后按子串匹配。
 *
 * 规则：
 * - **越具体越靠前**（例如 "neo9s pro+" 必须排在 "neo9" 前面），命中即返回；
 * - 同时做"去掉空格/横线/下划线"的二次匹配，兼容 "Pixel 8 Pro" / "pixel8pro" / "SM-S928B" 这类写法；
 * - 未命中则回退到设置页的"容量表兜底值"（默认 5500），用户可自己改。
 *
 * 说明：容量为厂商标称典型值（部分机型有双电芯/不同版本，取常见值），尽力维护。
 * 表里没有你的机型时，直接在设置页填标称容量即可。
 */
object DeviceCapacity {

    private val TABLE: List<Pair<String, Int>> = listOf(
        // ================= iQOO =================
        // ---- Neo 系列 ----
        "neo9s pro+" to 5500, "neo9 s pro+" to 5500,
        "v2403a" to 5500, "pd2403" to 5500,            // Neo9S Pro+
        "neo9s pro" to 5500, "neo9 s pro" to 5500,
        "neo9 pro" to 5160, "neo9" to 5160,
        "neo8 pro" to 5000, "neo8" to 5000,
        "neo7 racing" to 5000, "neo7 se" to 5000, "neo7" to 5000,
        "neo6 se" to 4700, "neo6" to 4700,
        "neo5 se" to 4500, "neo5s" to 4500, "neo5" to 4400,
        "neo3" to 4500,
        // ---- 数字旗舰 ----
        "iQOO 13" to 6150, "iQOO 12 pro" to 5100, "iQOO 12" to 5000,
        "iQOO 11s" to 5000, "iQOO 11 pro" to 4700, "iQOO 11" to 5000,
        "iQOO 10 pro" to 4700, "iQOO 10" to 4700,
        "iQOO 9 pro" to 4700, "iQOO 9" to 4700,
        "iQOO 8 pro" to 4500, "iQOO 8" to 4350,
        "iQOO 7" to 4000, "iQOO 5" to 4500, "iQOO 3" to 4440,
        // ---- Z 系列 ----
        "z9x" to 6000, "z9 turbo" to 6000, "z9" to 6000,
        "z8x" to 6000, "z8" to 5000,
        "z7x" to 6000, "z7" to 5000,
        "z6x" to 6000, "z6" to 5000,
        "z5x" to 5000, "z5" to 5000, "z3" to 4500, "z1x" to 4500, "z1" to 4000,
        // ---- U 系列 ----
        "u8" to 5500, "u7" to 5500, "u6" to 5500, "u5" to 5000, "u3" to 5000, "u1" to 5000,

        // ================= vivo =================
        // ---- X 系列 ----
        "x100 ultra" to 5500, "x100 pro" to 5400, "x100s pro" to 5400,
        "x100s" to 5100, "x100" to 5000,
        "x90 pro+" to 4700, "x90 pro" to 4870, "x90s" to 4810, "x90" to 4810,
        "x80 pro" to 4700, "x80" to 4500,
        "x70 pro+" to 4450, "x70 pro" to 4450, "x70" to 4400,
        "x60 pro+" to 4200, "x60 pro" to 4200, "x60" to 4300,
        "x50 pro+" to 4350, "x50 pro" to 4315, "x50" to 4200,
        "x30 pro" to 4350, "x30" to 4350,
        "x27" to 4000, "x23" to 4000, "x21" to 3400,
        // ---- S 系列 ----
        "s19 pro" to 5500, "s19" to 6000,
        "s18 pro" to 5000, "s18" to 5000,
        "s17 pro" to 4700, "s17" to 4600,
        "s16 pro" to 4700, "s16" to 4600,
        "s15 pro" to 4700, "s15" to 4500,
        "s12" to 4200, "s10" to 4050, "s9" to 4500,
        // ---- Y 系列 ----
        "y100" to 5000, "y78" to 5000, "y77" to 4500, "y76" to 4100,
        "y55" to 5000, "y53" to 5000, "y36" to 5000, "y35" to 5000,
        "y33s" to 5000, "y33" to 5000, "y32" to 5000, "y31" to 5000,
        "y21" to 5000, "y20" to 5000,
        // ---- NEX / X Fold / X Flip ----
        "x fold3 pro" to 5700, "x fold3" to 5500, "x fold2" to 4800, "x fold" to 4600,
        "x flip" to 4400, "vivo nex 3" to 4500, "vivo nex" to 4000,

        // ================= 华为 =================
        "mate 60 pro+" to 5000, "mate 60 pro" to 5000, "mate 60" to 4750,
        "mate 50 pro" to 4700, "mate 50" to 4460,
        "mate 40 pro+" to 4400, "mate 40 pro" to 4400, "mate 40" to 4200,
        "mate 30 pro" to 4500, "mate 30" to 4200,
        "mate 20 pro" to 4200, "mate 20" to 4000,
        "mate x5" to 5060, "mate x3" to 4800, "mate x2" to 4500, "mate xs" to 4500,
        "p60 pro" to 4815, "p60" to 4815,
        "p50 pro" to 4360, "p50 pocket" to 4000, "p50" to 4100,
        "p40 pro+" to 4200, "p40 pro" to 4200, "p40" to 3800,
        "p30 pro" to 4200, "p30" to 3650,
        "p20 pro" to 4000, "p20" to 3400,
        "nova 12 ultra" to 4600, "nova 12 pro" to 4600, "nova 12" to 4600,
        "nova 11 pro" to 4500, "nova 11" to 4500,
        "nova 10 pro" to 4500, "nova 10" to 4000,
        "nova 9 pro" to 4000, "nova 9" to 4300,
        "nova 8 pro" to 4000, "nova 8" to 3800,
        "nova 7 pro" to 4000, "nova 7" to 4000,
        "nova 6" to 4100, "nova 5" to 3500,
        "畅享" to 5000, "enjoy" to 5000,

        // ================= 荣耀 =================
        "magic6 ultimate" to 5600, "magic6 pro" to 5600, "magic6" to 5450,
        "magic5 ultimate" to 5450, "magic5 pro" to 5450, "magic5" to 5100,
        "magic4 ultimate" to 4600, "magic4 pro" to 4600, "magic4" to 4800,
        "magic3 pro" to 4600, "magic3" to 4600,
        "magic v2" to 5000, "magic vs2" to 5000, "magic vs" to 4750, "magic v" to 4750,
        "100 pro" to 5000, "honor 100" to 5000,
        "90 pro" to 5000, "honor 90" to 5000,
        "80 pro" to 4800, "honor 80" to 4800,
        "70 pro+" to 4500, "70 pro" to 4500, "honor 70" to 4800,
        "60 pro" to 4300, "honor 60" to 4300,
        "50 pro" to 4300, "honor 50" to 4300,
        "x50 pro" to 5800, "x50i" to 4500, "honor x50" to 5800,
        "x40i" to 4000, "x40" to 5100,
        "x30i" to 4000, "x30 max" to 5000, "honor x30" to 4800,
        "x20 se" to 4000, "x20" to 4300,
        "x10 max" to 5000, "honor x10" to 4300,   // ← 荣耀 X10
        "x9b" to 5800, "x9a" to 4600, "x9" to 4000, "x8" to 4000, "x7" to 5000,
        "play 9t" to 6000, "play 8t" to 6000, "play 8" to 5200,
        "play 7t" to 6000, "play 7" to 5000, "play 6t" to 5000, "play 6" to 5000,
        "play 5t" to 5000, "play 5" to 4000, "play 4t" to 5000, "play 4" to 4000,
        "play 3" to 4000, "play" to 5000,
        "畅玩" to 5000,
        // 华为/荣耀 常见代号（EMUI 上报的型号串）
        "any-lx1" to 5000, "any-nx1" to 5000, "fne-nx9" to 5000, "fne-an00" to 5000,
        "ver-an10" to 4815, "mna-lx9" to 4360, "jny-lx1" to 4200, "ele-l29" to 4200,
        "mar-lx1a" to 4000, "lya-l29" to 4000, "clt-l29" to 4000, "ane-lx1" to 3400,
        "bkz-l21" to 4000, "col-l29" to 4000, "rky-lx1" to 5000,

        // ================= 三星 Galaxy =================
        // ---- S 系列 ----
        "sm-s928" to 5000, "sm-s926" to 4900, "sm-s921" to 4000,   // S24 系列
        "sm-s918" to 5000, "sm-s916" to 4700, "sm-s911" to 3900,   // S23 系列
        "sm-s908" to 5000, "sm-s906" to 4500, "sm-s901" to 3700,   // S22 系列
        "sm-g998" to 5000, "sm-g996" to 4800, "sm-g991" to 4000,   // S21 系列
        "sm-g988" to 5000, "sm-g986" to 4500, "sm-g981" to 4000,   // S20 系列
        "sm-g975" to 4100, "sm-g973" to 3400, "sm-g970" to 3100,   // S10 系列
        "sm-g965" to 3500, "sm-g960" to 3000, "sm-g955" to 3500, "sm-g950" to 3000,
        "sm-g935" to 3600, "sm-g930" to 3000, "sm-g928" to 3000, "sm-g920" to 2550,
        // ---- Note 系列 ----
        "sm-n986" to 4500, "sm-n981" to 4300,
        "sm-n975" to 4300, "sm-n970" to 3500,
        "sm-n960" to 4000, "sm-n950" to 3300, "sm-n935" to 3300, "sm-n920" to 3000,
        // ---- Z 折叠 ----
        "sm-f946" to 4400, "sm-f936" to 4400, "sm-f926" to 4400,
        "sm-f916" to 4500, "sm-f907" to 4380, "sm-f900" to 4380,
        "sm-f741" to 4000, "sm-f731" to 3700, "sm-f721" to 3700,
        "sm-f711" to 3300, "sm-f707" to 3300, "sm-f700" to 3300,
        // ---- A 系列 ----
        "sm-a556" to 5000, "sm-a546" to 5000, "sm-a536" to 5000,   // A55/A54/A53
        "sm-a525" to 4500, "sm-a526" to 4500, "sm-a515" to 4000,   // A52/A51
        "sm-a356" to 5000, "sm-a346" to 5000, "sm-a336" to 5000,   // A35/A34/A33
        "sm-a245" to 5000, "sm-a235" to 5000, "sm-a225" to 5000,   // A24/A23/A22
        "sm-a155" to 5000, "sm-a146" to 5000, "sm-a145" to 5000,   // A15/A14
        "sm-a135" to 5000, "sm-a125" to 5000, "sm-a115" to 5000,   // A13/A12/A11
        "sm-a055" to 5000, "sm-a045" to 5000, "sm-a035" to 5000,   // A05/A04/A03
        "sm-a736" to 5000, "sm-a725" to 5000, "sm-a715" to 4500,   // A73/A72/A71
        "sm-a705" to 4500, "sm-a606" to 4500, "sm-a605" to 3500,
        "sm-a325" to 5000, "sm-a315" to 5000, "sm-a305" to 4000,
        "sm-a217" to 5000, "sm-a207" to 5000, "sm-a205" to 4000,
        "sm-a107" to 5000, "sm-a105" to 4000, "sm-a047" to 5000, "sm-a037" to 5000,
        "sm-a022" to 5000, "sm-a013" to 3000, "sm-a015" to 3000,
        // ---- M / F / J 系列 ----
        "sm-m536" to 5000, "sm-m526" to 5000, "sm-m336" to 5000, "sm-m325" to 5000,
        "sm-m135" to 5000, "sm-m127" to 5000, "sm-m115" to 5000,
        "sm-f556" to 5000, "sm-f546" to 5000, "sm-f426" to 5000, "sm-f415" to 6000,
        "sm-j" to 3000,

        // ================= Google Pixel =================
        "pixel 9 pro xl" to 5060, "pixel 9 pro fold" to 4650,
        "pixel 9 pro" to 4700, "pixel 9a" to 5100, "pixel 9" to 4700,
        "pixel 8 pro" to 5050, "pixel 8a" to 4492, "pixel 8" to 4575,
        "pixel 7 pro" to 5000, "pixel 7a" to 4385, "pixel 7" to 4355,
        "pixel 6 pro" to 5003, "pixel 6a" to 4410, "pixel 6" to 4614,
        "pixel 5a" to 4680, "pixel 5" to 4080, "pixel 4a" to 3140,
        "pixel 4 xl" to 3700, "pixel 4" to 2800,
        "pixel 3a" to 3000, "pixel 3" to 2915, "pixel 2" to 2700, "pixel" to 2770,

        // ================= 小米 / 红米 / POCO =================
        "xiaomi 14 ultra" to 5300, "xiaomi 14 pro" to 4880, "xiaomi 14" to 4610,
        "xiaomi 13 ultra" to 5000, "xiaomi 13 pro" to 4820, "xiaomi 13" to 4500,
        "xiaomi 12s ultra" to 4860, "xiaomi 12s pro" to 4600, "xiaomi 12s" to 4500,
        "xiaomi 12 pro" to 4600, "xiaomi 12" to 4500,
        "xiaomi 11 ultra" to 5000, "mi 11" to 4600, "mi 10" to 4780, "mi 9" to 3300,
        "mix fold 3" to 4800, "mix fold 2" to 4500, "mix fold" to 5020,
        "mix 4" to 4500, "mix 3" to 3200,
        "civi 4" to 4700, "civi 3" to 4500, "civi 2" to 4500, "civi" to 4500,
        // ---- 红米 K 系列 / Turbo：型号代码（代码取自小米型号对照表，容量取自充电头网全系盘点）----
        "21121210c" to 4700,   // Redmi K50 电竞版
        "22011211c" to 5000,   // Redmi K50 Pro
        "22021211rc" to 4500,   // Redmi K40S
        "22041211ac" to 5500,   // Redmi K50
        "22081212c" to 5000,   // Redmi K50 至尊版
        "22122rk93c" to 5500,   // Redmi K60E
        "22127rk46c" to 5000,   // Redmi K60 Pro
        "23013rk75c" to 5500,   // Redmi K60
        "23078rkd5c" to 5000,   // Redmi K60 至尊版
        "23113rkc6c" to 5000,   // Redmi K70
        "23117rk66c" to 5000,   // Redmi K70 Pro
        "2311drk48c" to 5500,   // Redmi K70E
        "24069ra21c" to 5000,   // Redmi Turbo 3
        "2407frk8ec" to 5500,   // Redmi K70 至尊版
        "24117rk2cc" to 6550,   // Redmi K80
        "24122rkc7c" to 6000,   // Redmi K80 Pro
        "24127rk2cc" to 6000,   // Redmi K80 Pro 冠军版
        "24129rt7cc" to 6550,   // Redmi Turbo 4
        "25053rt47c" to 7550,   // Redmi Turbo 4 Pro
        "25060rk16c" to 7500,   // Redmi K80 至尊版
        "25102rk69c" to 7560,   // Redmi K90 Pro Max
        "25102rkbec" to 7560,   // Redmi K90 Pro Max
        "2510drk44c" to 7100,   // Redmi K90
        "2511frt34c" to 7560,   // Redmi Turbo 5
        "2602brt18c" to 9000,   // Redmi Turbo 5 Max
        "2604frk1ec" to 8550,   // Redmi K90 Max
        "2606frt34i" to 7560,   // Redmi Turbo 5
        "m098fe" to 9070,   // Redmi K100 Pro Max
        "m1903f10a" to 4000,   // Redmi K20
        "m1903f10c" to 4000,   // Redmi K20
        "m1903f10i" to 4000,   // Redmi K20
        "m1903f11a" to 4000,   // Redmi K20 Pro
        "m1903f11a" to 4000,   // Redmi K20 Pro 尊享版
        "m1903f11c" to 4000,   // Redmi K20 Pro
        "m1903f11i" to 4000,   // Redmi K20 Pro
        "m1912g7bc" to 4500,   // Redmi K30 4G
        "m1912g7be" to 4500,   // Redmi K30 4G
        "m2001g7ac" to 4500,   // Redmi K30 5G
        "m2001g7ac" to 4500,   // Redmi K30i 5G
        "m2001g7ae" to 4500,   // Redmi K30 5G
        "m2001j11c" to 4700,   // Redmi K30 Pro
        "m2006j10c" to 4500,   // Redmi K30 至尊纪念版
        "m2007j3sc" to 5000,   // Redmi K30S 至尊纪念版
        "m2012k10c" to 5065,   // Redmi K40 游戏增强版
        "m2012k11ac" to 4520,   // Redmi K40
        "m2012k11c" to 4520,   // Redmi K40 Pro
        "m2012k11c" to 4520,   // Redmi K40 Pro+
        "m332bf" to 8550,   // Redmi K90 至尊版
        "m511cd" to 9070,   // Redmi K100 Pro
        // ---- 红米 K 系列 / Turbo：市场名兜底（部分地区/ROM 会上报市场名）----
        "redmi k100 pro max" to 9070, "redmi k100 pro" to 9070,
        "redmi k90 pro max" to 7560, "redmi k90 max" to 8550, "redmi k90" to 7100,
        "redmi k80 ultra" to 7500, "redmi k80 pro" to 6000, "redmi k80" to 6550,
        "redmi k70 ultra" to 5500, "redmi k70e" to 5500, "redmi k70 pro" to 5000, "redmi k70" to 5000,
        "redmi k60 ultra" to 5000, "redmi k60e" to 5500, "redmi k60 pro" to 5000, "redmi k60" to 5500,
        "redmi k50 ultra" to 5000, "redmi k50 电竞版" to 4700, "redmi k50 pro" to 5000, "redmi k50" to 5500,
        "redmi k40 游戏增强版" to 5065, "redmi k40 pro" to 4520, "redmi k40s" to 4500, "redmi k40" to 4520,
        "redmi k30s" to 5000, "redmi k30 pro" to 4700, "redmi k30i" to 4500, "redmi k30" to 4500,
        "redmi k20 pro" to 4000, "redmi k20" to 4000,
        "redmi turbo 5 max" to 9000, "redmi turbo 5" to 7560,
        "redmi turbo 4 pro" to 7550, "redmi turbo 4" to 6550, "redmi turbo 3" to 5000,
        "redmi note 13 pro" to 5100, "redmi note 13" to 5000,
        "redmi note 12" to 5000, "redmi note 11" to 5000, "redmi note 10" to 5000,
        "redmi note 9" to 5020, "redmi note 8" to 4000, "redmi note 7" to 4000,
        "redmi 13c" to 5000, "redmi 12c" to 5000, "redmi 12" to 5000,
        "redmi 10a" to 5000, "redmi 10" to 5000, "redmi 9a" to 5000, "redmi 9" to 5020,
        "poco f6 pro" to 5000, "poco f6" to 5000, "poco f5" to 5000, "poco f4" to 4500,
        "poco f3" to 4520, "poco x6 pro" to 5100, "poco x6" to 5100,
        "poco x5" to 5000, "poco x4" to 5000, "poco x3" to 5160,
        "poco m6" to 5030, "poco m5" to 5000, "poco m4" to 5000, "poco m3" to 6000,
        "poco c65" to 5000, "poco c55" to 5000, "poco c40" to 6000,

        // ================= OPPO =================
        "find x7 ultra" to 5000, "find x7" to 5000,
        "find x6 pro" to 5000, "find x6" to 4800,
        "find x5 pro" to 5000, "find x5" to 4800, "find x3 pro" to 4500, "find x3" to 4500,
        "find x2 pro" to 4260, "find x2" to 4200, "find x" to 3730,
        "find n3" to 4805, "find n2" to 4520, "find n" to 4500,
        "find n3 flip" to 4300, "find n2 flip" to 4300,
        "reno 12 pro" to 5000, "reno 12" to 5000,
        "reno 11 pro" to 4700, "reno 11" to 4800,
        "reno 10 pro+" to 4700, "reno 10 pro" to 4600, "reno 10" to 5000,
        "reno 9 pro" to 4700, "reno 9" to 4500,
        "reno 8 pro" to 4500, "reno 8" to 4500,
        "reno 7 pro" to 4500, "reno 7" to 4500,
        "reno 6 pro" to 4500, "reno 6" to 4300,
        "reno 5 pro" to 4350, "reno 5" to 4300, "reno 4" to 4000, "reno 3" to 4025,
        "oppo a" to 5000, "oppo k" to 5000,

        // ================= 一加 OnePlus =================
        "oneplus 13" to 6000, "oneplus 12" to 5400, "oneplus 11" to 5000,
        "oneplus 10 pro" to 5000, "oneplus 10t" to 4800, "oneplus 9 pro" to 4500,
        "oneplus 9" to 4500, "oneplus 8t" to 4500, "oneplus 8" to 4300,
        "oneplus 7t" to 3800, "oneplus 7" to 3700, "oneplus 6t" to 3700,
        "oneplus turbo 6" to 9000, "oneplus turbo 5" to 8000, "plu110" to 9000,
        "oneplus ace 6 pro" to 7800, "oneplus ace 6" to 7800,
        "oneplus ace 3 pro" to 6100, "oneplus ace 3" to 5500, "oneplus ace 2" to 5000,
        "oneplus ace" to 4500, "oneplus nord 4" to 5500, "oneplus nord 3" to 5000,
        "oneplus nord 2" to 4500, "oneplus nord" to 4115,
        "le2100" to 4500, "le2120" to 4500, "in2010" to 4300, "kb2000" to 4500,

        // ================= realme =================
        "realme gt7 pro" to 5800, "realme gt6" to 5500, "realme gt5 pro" to 5400,
        "realme gt5" to 5240, "realme gt neo5" to 4600, "realme gt neo3" to 4500,
        "realme gt2" to 5000, "realme gt" to 4500,
        "realme 12 pro" to 5000, "realme 11 pro" to 5000, "realme 10 pro" to 5000,
        "realme 9 pro" to 5000, "realme 8" to 4500, "realme 7" to 4500,
        "realme c67" to 5000, "realme c55" to 5000, "realme c35" to 5000, "realme c25" to 6000,
        "realme narzo" to 5000, "rmx" to 5000,

        // ================= 摩托罗拉 / 联想 =================
        "moto g84" to 5000, "moto g54" to 5000, "moto g73" to 5000, "moto g62" to 5000,
        "moto g52" to 5000, "moto g31" to 5000, "moto g30" to 5000,
        "moto edge 50" to 5000, "moto edge 40" to 4400, "moto edge 30" to 4020,
        "moto edge 20" to 4020, "moto razr 40 ultra" to 3800, "moto razr 40" to 4200,
        "moto razr 2022" to 3500, "xt2" to 5000,
        "lenovo legion y90" to 5600, "legion y70" to 5100, "legion duel" to 5000,
        "tb3" to 5000, "tb1" to 5000,

        // ================= 索尼 =================
        "xperia 1 vi" to 5000, "xperia 1 v" to 5000, "xperia 1 iv" to 5000,
        "xperia 1 iii" to 4500, "xperia 1 ii" to 4000, "xperia 1" to 3330,
        "xperia 5 v" to 5000, "xperia 5 iv" to 5000, "xperia 5 iii" to 4500,
        "xperia 5 ii" to 4000, "xperia 5" to 3140,
        "xperia 10 vi" to 5000, "xperia 10 v" to 5000, "xperia 10 iv" to 5000,
        "xperia 10 iii" to 4500, "xperia 10 ii" to 3600, "xperia 10" to 2870,
        "xperia pro" to 4000, "xq-" to 4500,

        // ================= 华硕 / 其他 =================
        "rog phone 8" to 5500, "rog phone 7" to 6000, "rog phone 6" to 6000,
        "rog phone 5" to 6000, "rog phone 3" to 6000, "rog phone 2" to 6000,
        "zenfone 11" to 5500, "zenfone 10" to 4300, "zenfone 9" to 4300,
        "zenfone 8" to 4000, "zenfone 7" to 5000, "zenfone 6" to 5000,
        "asus_ai" to 6000, "asus_i" to 6000, "asus_z" to 4300,
        "nothing phone (2a)" to 5000, "nothing phone (2)" to 4700,
        "nothing phone (1)" to 4500, "a063" to 4500, "a065" to 5000, "a142" to 5000,
        "nubia z60" to 6000, "nubia z50" to 5000, "nubia z40" to 5000,
        "red magic 9" to 6500, "red magic 8" to 6000, "red magic 7" to 5000,
        "red magic 6" to 5050, "red magic 5" to 4500, "nx7" to 6000, "nx6" to 5000,
        "meizu 21" to 4800, "meizu 20" to 4700, "meizu 18" to 4000, "meizu 17" to 4500,
        "zte axon 50" to 5000, "zte axon 40" to 5000, "zte axon 30" to 4200,
        "zte a" to 5000, "zte blade" to 5000,
        "black shark 5" to 4650, "black shark 4" to 4500, "black shark 3" to 4720,
        "sharp aquos r8" to 5000, "sharp aquos r7" to 5000, "sharp aquos" to 4500,
        "fairphone 5" to 4200, "fairphone 4" to 3905,
    )

    /** 去空格匹配的最小键长度：低于它的键（如 "u1"）只允许做 token 匹配，否则会误命中 "PLU110" 这类机型代码。 */
    private const val MIN_LOOSE_KEY_LEN = 6

    fun lookup(context: Context): Int {
        // 优先级：手填（强制） > 远程表 > 内置表 > 兜底值
        if (Prefs.forceCapacity(context)) return Prefs.capacityFallback(context)
        return matchKey()?.let { (_, cap) -> cap } ?: Prefs.capacityFallback(context)
    }

    /** 当前生效容量的来源说明，给设置页摘要和日志用。 */
    fun source(context: Context): String {
        if (Prefs.forceCapacity(context)) return "手填（强制）"
        matchIn(CapacityStore.remoteEntries())?.let { return "远程表:" + it.first }
        matchIn(TABLE)?.let { return "容量表:" + it.first }
        return "兜底值（表未命中）"
    }

    private fun haystack(): String =
        (Build.MODEL.orEmpty() + " | " + Build.DEVICE.orEmpty() + " | " +
                Build.PRODUCT.orEmpty() + " | " + Build.MANUFACTURER.orEmpty() + " | " +
                Build.BRAND.orEmpty()).lowercase()

    /** 先查远程表（可热更新），再查内置表。 */
    private fun matchKey(): Pair<String, Int>? =
        matchIn(CapacityStore.remoteEntries()) ?: matchIn(TABLE)

    /** 在给定表里按顺序匹配；未命中返回 null。 */
    private fun matchIn(table: List<Pair<String, Int>>): Pair<String, Int>? {
        if (table.isEmpty()) return null
        val hay = haystack()
        val compact = squash(hay)
        for (entry in table) {
            val key = entry.first
            // ① token 匹配：键必须从"词的边界"开始（前面是开头或非字母数字）。
            //    这样 "u1" 不会命中 "plu110"，但 "sm-s928" 仍能命中 "sm-s928b"。
            if (matchesTokenPrefix(hay, key)) return entry
            // ② 去掉空格/横线的松散匹配：只对足够长的键生效，避免短键误伤机型代码。
            val k = squash(key)
            if (k.length >= MIN_LOOSE_KEY_LEN && compact.contains(k)) return entry
        }
        return null
    }

    /**
     * 短键（<4 字符）的危险性：像 "u1" 会命中机型代码 "PLU110"，"x9" 会命中 "SM-X910"。
     * 所以短键只允许紧跟在【字符串开头 / 空格 / 字段分隔符】之后，即必须是一个独立的"词"。
     * 长键（≥4，如 "sm-s928"）仍用宽松边界，这样它才能命中 "SM-S928B" 这种带后缀的代码。
     */
    private const val SHORT_KEY_LEN = 4

    /** key 是否作为"某个词的起始片段"出现在 hay 中。 */
    private fun matchesTokenPrefix(hay: String, key: String): Boolean {
        val strict = key.length < SHORT_KEY_LEN
        var from = 0
        while (true) {
            val i = hay.indexOf(key, from)
            if (i < 0) return false
            if (i == 0) return true
            val prev = hay[i - 1]
            val ok = if (strict) {
                prev == ' ' || prev == '|' || prev == '(' || prev == '/' || prev == ','
            } else {
                !prev.isLetterOrDigit()
            }
            if (ok) return true
            from = i + 1
        }
    }

    /** 去掉空格/横线/下划线，便于 "Pixel 8 Pro" 与 "pixel8pro"、"SM-S928B" 与 "sms928b" 互相匹配。 */
    private fun squash(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c != ' ' && c != '-' && c != '_') sb.append(c)
        }
        return sb.toString()
    }

    /** 仅供调试/日志：返回命中的键名，未命中返回 null。 */
    fun matchedKey(): String? = matchKey()?.first

    /** 内置表条数。 */
    val size: Int get() = TABLE.size
}
