# T-account book 微信小程序转 Android 应用 - 产品需求文档

## Overview
- **Summary**: 将微信小程序「T-account book」（专业股票做T/日内回转交易记账工具）转换为可在 Android 手机上独立运行的原生应用，在当前工作目录 `e:\小程序\android-Tapp` 中交付完整 Android Studio 工程并尝试本机构建 APK。
- **Purpose**: 用户希望脱离微信环境，在安卓手机上使用同一套做T记账能力（底仓管理、正T买入/反T卖出、补单、归档、利润统计、预估交易、数据备份），并能把小程序中的历史数据迁移到 Android 版。
- **Target Users**: 个人股票投资者（A股/港股/ETF），使用「做T」策略记录日内回转交易、跟踪持仓成本与做T利润。

## Goals
- 用 **Kotlin + Jetpack Compose** 原生实现小程序全部 9 个页面的功能与交互。
- **1:1 忠实移植** `app.js` 中的核心业务口径：费用计算、持仓成本回放、做T利润、归档底仓调整、利润统计。
- 数据本地持久化，离线可用，无需任何网络/后端。
- 备份文件与小程序**完全互通**：小程序导出的 CSV/JSON 备份可直接导入 Android 版，反之亦然。
- 在本机安装 Android 命令行 SDK 并执行 `gradlew assembleDebug` 产出可安装的 debug APK。

## Non-Goals
- 不实现实时行情/联网股价（小程序中 `refreshStockPrices` 已注释停用，`currentPrice` 仅为本地字段，统计页"实时"显示为占位）。
- 不实现微信登录、用户体系、云开发、位置权限等微信平台能力。
- 不实现小程序中孤立的 `pages/trade-rate/trade-rate` 独立页（无任何页面跳转入口，费率设置已内联在"我的"页；`services/`、`utils/` 目录为未被页面引用的遗留代码，不移植）。
- 不做 iOS、平板适配、深色模式（小程序固定浅色主题）。
- 不实现 Excel(.xlsx) 解析；备份格式为小程序既有的自定义 CSV（`.csv`/`.txt`）与 JSON。

## Background & Context

### 小程序现状（源码：`E:\小程序\t-assetbook`）
- 框架：原生微信小程序（app.js + 9 个 Page），无第三方 UI/图表库，统计页曲线用 `wx.createCanvasContext` 手绘。
- **真实业务层是 `app.js`**（928 行）：所有页面通过 `const app = getApp()` 调用 `app.globalData`（assets/trades/settings）与 app 方法；`services/asset-service.js`、`utils/*` 为另一套未被页面引用的遗留实现，**不作为移植依据**。
- 存储：`wx.setStorageSync` 三个键：`assets`、`trades`、`settings`。
- 页面清单（app.json）：
  | 页面 | 功能 |
  |---|---|
  | pages/assets（Tab 股票） | 资产卡片列表：名称/市场/交易所、总持仓成本、股数、成本价；正T买入/反T卖出/预估交易按钮；展开价格梯；交易记录/编辑入口；搜索；添加资产；做T战绩弹窗 |
  | pages/add-asset | 添加/编辑资产：名称、市场(A股/港股/ETF)、交易所联动(沪市/深市/港交所)、底仓股数、底仓单价、底仓总成本（股数×单价联动）；编辑模式可删除 |
  | pages/trade-operation | 交易操作核心页，承载 4 种状态：新建正T买入(buyNoSell)、新建反T卖出(sellNoBuy)、补充卖出(buyNoSell→buyToSell)、补充买入(sellNoBuy→sellToBuy)、编辑买入/卖出/T完成记录；实时试算佣金/过户费/印花税/净额；交易日期选择；备注 |
  | pages/trade-record | 单资产交易记录：筛选 Tab（未T完 buyNoSell/sellNoBuy、T完成 buyToSell/sellToBuy、归档 isArchived）；排序（完成时间/买入价）；费用明细展开；归档、删除、清空；总/本月/今日利润汇总；补买补卖/编辑入口 |
  | pages/statistics（Tab 统计） | 总/本月/今日做T利润汇总；每只股票当月每日成本价折线图（canvas 手绘，触摸显示日期/股数/成本价/总成本 tooltip）；利润日历（月份切换、日历格显示当日盈亏、点选日期查看分股票收益） |
  | pages/mine（Tab 我的） | 数据导出/导入入口；通用交易费率内联设置（A股/港股/ETF 佣金，单位‱）；清空所有数据（二次确认）；关于；版本号 |
  | pages/estimate-trade | 预估交易：输入预估股价、成本价、总股数、单次卖出股数、阶梯比例(%)，生成最多 100 行阶梯卖出测算表（价格/卖出额/剩余股数/累计收入/盈亏） |
  | pages/export-config | 导出配置：格式 CSV/JSON、范围 全部/仅资产/仅交易、时间范围 全部/今日/本周/本月/自定义；预览前 20 行；写文件后 saveFileToDisk，失败回退剪贴板 |
  | pages/trade-rate | 孤立页（无入口），不移植 |

### 关键业务口径（必须与 app.js 逐行对齐）
- **数据模型**
  - Asset：`id, name, market, exchange, baseTotalCost(底仓总成本), baseShares(底仓股数), baseUnit(底仓单价), totalCost(现持仓总成本), shares(现持仓股数), costPerShare(成本价,显示3位), totalProfit/monthProfit/todayProfit, currentPrice, isTop, remark, createTime, updateTime`。
  - Trade：`id, assetId, assetName, tradeStatus(buyNoSell/buyToSell/sellNoBuy/sellToBuy), buyPrice/buyShares/buyCommission/buyTransferFee/buyTotalFee/buyTotal/buyTradeDate, sellPrice/sellShares/sellCommission/sellTransferFee/sellStampDuty/sellTotalFee/sellTotal(=净额)/sellTradeDate, profit, remark, tradeDate, tradeTime, isArchived, archiveTime`，外加兼容旧字段 `price/shares/total/commission/transferFee/stampDuty`。
  - Settings：`tradeRateA/tradeRateHK/tradeRateETF`（佣金率，单位**万分之一‱**，A股缺省 0.85‱）、`tradeRateTransfer`（过户费，缺省 '0.1'‱）、`tradeRateStampDuty`（印花税，缺省 '5'‱，仅卖出）、`defaultMarket('A股')`、`defaultExchange('沪市')`、`theme`。
- **费用计算**（app.js addTBuy/addTSell 与 trade-operation 页 `_calcFees`）：
  - 佣金 = max(成交金额 × 佣金率, 5)，四舍五入 2 位；佣金率 = `tradeRateA / 10000`，无配置时 0.000085。
  - 过户费 = 成交金额 × (`tradeRateTransfer`/10000)，2 位小数（缺省 0.00001）。
  - 印花税 = 卖出时 成交金额 × (`tradeRateStampDuty`/10000)，2 位小数（缺省 0.0005）；买入为 0。
- **正T买入**：`buyTotal = 价×股数 + 佣金 + 过户费`；持仓 `totalCost += buyTotal`、`shares += 股数`、`costPerShare = totalCost/shares`（6 位小数精度）。
- **反T卖出**：校验持仓股数充足；`sellTotal(净额) = 价×股数 - (佣金+过户费+印花税)`；做T利润 `profit = (卖价 - costPerShare) × 股数 - 总费用`；持仓 `totalCost -= sellTotal`、`shares -= 股数`，不足归零，成本价重算。
- **补单**：买入后补卖 → 原记录状态置 `buyToSell`，填卖出字段，`profit = sellTotal - buyTotal`，持仓按卖出净额扣减；卖出后补买 → 置 `sellToBuy`，`profit = sellTotal - buyTotal`，持仓按买入总额增加。编辑 T完成记录需"撤销旧值+应用新值"重算持仓。
- **持仓回放**（assets/trade-record/statistics 页共用逻辑）：从底仓起，按日期升序（同日先买后卖）重放所有**未归档**交易：买加 `buyTotal`/股数，卖减 `sellTotal`/股数（不为负），得 `totalCost/shares/costPerShare`（成本价显示 3 位）。
- **归档 archiveTrade**：归档记录仅保留 `id/assetId/assetName/tradeStatus/tradeDate/buyTotal/sellTotal/profit/isArchived/archiveTime`；`adjustBaseUnitAfterArchive` 把该笔交易对成本/股数的净影响（`buyTotal-sellTotal`、`buyShares-sellShares`）转移进底仓，保证归档前后持仓成本价不变；归档后持仓重置为底仓值。
- **利润统计**：已归档交易用归档时保存的 `profit`；未归档 T完成（buyToSell/sellToBuy）用 `sellTotal - buyTotal`；利润归属日期取买卖日期较晚者；总/本月/今日三个口径。
- **预估交易**：阶梯步长 `priceStep = 预估股价 × (比例/100)`；第 i 行价格 = 预估价 + (i-1)×步长；每次卖出 `min(单次股数, 剩余股数)`；累计收入累加；`盈亏 = 单价×剩余股数 + 累计收入 - 总成本`，直到剩余股数 ≤ 0 或 100 行。
- **备份格式**（app.js jsonToCsv/csvToJson，必须互通）：
  - CSV 为纯文本分段：`===资产数据===`（15 列中文表头）、`===交易记录===`（30 列中文表头）、`===交易明细表===`；字段含逗号用双引号包裹；导出 CSV 带 UTF-8 BOM。
  - JSON 导出为 `{version:'2.0.0', exportTime, exportSettings, assets[], trades[]}`。
  - 导入时校验必须包含 `===资产数据===` 或 `===交易记录===` 段标记，统计各段条数后二次确认，**覆盖式导入**。
- 日期：小程序用 `new Date().toLocaleDateString()`（中文环境为 `yyyy/M/d`）存储 tradeDate；解析需兼容 `yyyy/M/d`、`yyyy-M-d`、ISO 等格式。

### 本机环境
- Windows；已装 Java 24（`java -version` 可用）；**未检测到 Android SDK**（ANDROID_HOME 为空、默认目录不存在）；未装 Node；PowerShell 执行策略禁止 .ps1 脚本（交付与构建步骤不得依赖 ps1 脚本，使用 gradlew.bat / 原生命令）。
- 用户已确认：由我方下载安装 Android commandline-tools + SDK 组件并尝试命令行构建 debug APK。

## Functional Requirements
- **FR-1 工程与导航**：单 Activity + Compose Navigation；底部三 Tab（股票/统计/我的，主色 #1565C0）；股票 Tab 可进入 添加资产、交易操作、交易记录、预估交易；我的 Tab 可进入 导出配置；所有二级页带返回。
- **FR-2 资产 CRUD**：添加（名称必填、市场/交易所联动、底仓股数与单价联动算总成本、允许零底仓）、编辑、删除（确认弹窗）、置顶切换；列表支持按名称搜索；卡片展示总持仓成本/股数/成本价及买/卖价格梯展开。
- **FR-3 交易操作**：支持新建正T买入、新建反T卖出（股数不足拦截）、补卖、补买、编辑买入/卖出/T完成共 7 种入口形态；输入价格/股数实时试算佣金、过户费、印花税、手续费合计、买入总额/卖出净额；交易日期可改（默认今天）；备注；成功后返回并刷新。
- **FR-4 交易记录**：未T完/T完成/归档三筛选；完成时间/买入价排序；每条可展开费用明细；操作含编辑、补买/补卖、归档（确认）、删除（确认）；清空该资产全部交易（确认后重置持仓）；顶部展示该资产总/本月/今日利润与当前持仓。
- **FR-5 统计**：汇总总/本月/今日做T利润；每只股票一张当月成本价折线图（触摸查看当日日期/股数/成本价/总成本）；利润日历支持上/下月切换，日历格显示当日盈亏（盈红亏绿），点选日期展示当日分股票收益明细。
- **FR-6 预估交易**：表单（预估股价、成本价、总股数、单次卖出股数、阶梯比例%，成本价/股数从资产带入）→ 生成阶梯测算表（可滚动），列：序号/价格/卖出额/剩余股数/总价/累计收入/盈亏。
- **FR-7 我的与费率**：展示资产数/交易数等统计；A股/港股/ETF 佣金率（‱）内联编辑保存；清空全部数据（两次确认）；关于弹窗；数据导出/导入入口。
- **FR-8 导出**：CSV/JSON 格式、全部/仅资产/仅交易范围、全部/今日/本周/本月/自定义时间范围；内容预览；通过系统文件选择器（SAF ACTION_CREATE_DOCUMENT）保存到用户指定位置，CSV 带 BOM；默认文件名 `tassetbook_export_yyyyMMdd.csv|json`。
- **FR-9 导入**：通过系统文件选择器（ACTION_OPEN_DOCUMENT）选择 .csv/.txt/.json；校验段标记/格式；展示资产/交易条数与文件信息后二次确认；覆盖导入并报告结果。
- **FR-10 持久化**：assets/trades/settings 任何变更后落盘（应用内部存储 JSON 文件）；启动时加载并过滤无效项；杀进程重启数据完整恢复。

## Non-Functional Requirements
- **NFR-1 离线可用**：无任何网络权限需求（不申请 INTERNET）；全部计算本地完成。
- **NFR-2 数值一致性**：金额计算与小程序同精度（内部 6 位小数、费用 2 位、成本价显示 3 位），关键场景结果与小程序口径偏差为 0。
- **NFR-3 性能**：500 条交易以内列表/回放/日历计算在手机上无感知卡顿（<300ms）。
- **NFR-4 可构建**：工程可用 Android Studio 直接打开运行；命令行 `gradlew.bat assembleDebug` 在本机安装 SDK 后构建成功并产出 APK。
- **NFR-5 兼容性**：minSdk 24（Android 7.0），target/compile SDK 35；中文界面；竖屏手机。
- **NFR-6 交付边界**：所有新增文件仅位于 `e:\小程序\android-Tapp` 目录内，不改动小程序源码与其他目录。

## Constraints
- **Technical**: Kotlin + Jetpack Compose + Material3；Gson 做本地 JSON 序列化；Compose Canvas 自绘折线图；SAF 做文件导入导出；Gradle KTS；不依赖 PowerShell 脚本。
- **Business**: 计算口径、费率单位（‱）、状态机（4 种 tradeStatus + 归档）、备份 CSV 分段格式必须与小程序一致，确保老用户数据无缝迁移。
- **Dependencies**: 需联网下载 Android cmdline-tools、platform-35、build-tools、Gradle 发行包与 Maven 依赖（首次构建）；JDK 24 与 Gradle/AGP 的兼容性需在实现阶段验证（必要时选用支持 JDK 24 的 Gradle/AGP 版本组合）。

## Assumptions
- 用户数据迁移路径：在小程序中用"数据导出"生成备份文件 → 传到安卓手机 → Android 版"数据导入"。
- 小程序"实时"股价无真实数据源，Android 版不实现；相关字段保留但不展示实时行情。
- 盈亏配色沿用小程序惯例（盈利红、亏损绿），如与小程序样式有出入以源码 wxss 为准。
- 应用名 "T-account book"，包名 `com.tassetbook.app`。

## Acceptance Criteria

### AC-1: 工程可构建并产出 APK
- **Type**: `rule`
- **Given**: 本机已安装 Android cmdline-tools 与 SDK 组件
- **When**: 在 `e:\小程序\android-Tapp` 执行 `gradlew.bat assembleDebug`
- **Then**: 构建成功，产出 `app/build/outputs/apk/debug/app-debug.apk`
- **Pass Condition**: 退出码 0 且 APK 文件存在
- **Evidence**: 构建日志与 APK 文件路径/大小

### AC-2: 资产增删改与持久化
- **Type**: `rule`
- **Given**: 空数据应用
- **When**: 添加资产（名称"测试"、A股/沪市、底仓 1000 股、单价 10 元）→ 杀进程重启
- **Then**: 列表显示该资产，底仓总成本 10000.00、股数 1000、成本价 10.000；重启后数据仍在；编辑与删除（含确认）均生效
- **Pass Condition**: 上述每一步均可在代码逻辑/单元测试中验证且与 app.js addAsset/updateAsset/deleteAsset 口径一致
- **Evidence**: 单元测试 + 工程代码

### AC-3: 正T买入费用与持仓更新
- **Type**: `rule`
- **Given**: 底仓 1000 股/总成本 10000 的资产，佣金率 0.85‱、过户费 0.1‱
- **When**: 正T买入 100 股、价格 10 元
- **Then**: 佣金 5.00（最低 5 元）、过户费 0.01、买入总额 1005.01；持仓 totalCost=11005.01、shares=1100、成本价 10.005（3 位）
- **Pass Condition**: 单元测试断言全部数值与 app.js addTBuy 手算结果一致
- **Evidence**: JVM 单元测试输出

### AC-4: 反T卖出费用、利润与持仓扣减
- **Type**: `rule`
- **Given**: 持仓 1100 股、成本价 10.005、总成本 11005.01；印花税 5‱
- **When**: 反T卖出 100 股、价格 11 元
- **Then**: 佣金 5.00、过户费 0.01、印花税 0.55、净额 1094.44；做T利润=(11-10.005)×100-5.56=93.94；持仓 totalCost=9910.57、shares=1000
- **Pass Condition**: 单元测试断言费用、净额、利润、持仓与 app.js addTSell 一致；卖出股数超过持仓时被拦截
- **Evidence**: JVM 单元测试输出

### AC-5: 补单与编辑状态机
- **Type**: `rule`
- **Given**: 一条 buyNoSell 记录与一条 sellNoBuy 记录
- **When**: 分别执行补卖（→buyToSell）与补买（→sellToBuy），再编辑该 T完成记录的价格/股数
- **Then**: 状态、profit=sellTotal-buyTotal、买卖字段、持仓成本/股数变化与 trade-operation.js 各分支逻辑一致
- **Pass Condition**: 单元测试覆盖 4 种状态 × 新建/补单/编辑分支并断言持仓重算结果
- **Evidence**: JVM 单元测试输出

### AC-6: 归档与底仓调整
- **Type**: `rule`
- **Given**: 含一笔已完成 T（buyToSell）的资产
- **When**: 执行归档
- **Then**: 记录变为 isArchived 且仅保留盈亏字段；底仓 baseShares/baseTotalCost/baseUnit 按 adjustBaseUnitAfterArchive 调整；归档前后 totalCost/shares/costPerShare 保持不变；归档记录出现在"归档"筛选
- **Pass Condition**: 单元测试断言归档前后成本价不变、底仓数值与 app.js 一致
- **Evidence**: JVM 单元测试输出

### AC-7: 交易记录页筛选/排序/操作
- **Type**: `rule`
- **Given**: 含多种状态与归档记录的资产
- **When**: 切换 未T完/T完成/归档 筛选、切换排序、展开费用、归档/删除/清空
- **Then**: 列表内容、排序、利润汇总（总/本月/今日）与 trade-record.js 口径一致；清空后该资产持仓归零
- **Pass Condition**: 筛选/排序/汇总逻辑有单元测试覆盖，UI 操作链路完整
- **Evidence**: 单元测试 + 代码走查

### AC-8: 统计图表与利润日历
- **Type**: `rule`
- **Given**: 跨多日的交易数据
- **When**: 进入统计 Tab，切换月份、点选日历某日、触摸折线图
- **Then**: 总/本月/今日利润数值正确；每只股票折线图对应当月每日回放成本价；日历格显示当日盈亏且点击后列出分股票收益
- **Pass Condition**: 日历/曲线数据构造逻辑与 statistics.js 回放逻辑一致（单元测试），UI 可交互
- **Evidence**: 单元测试 + 代码走查

### AC-9: 预估交易测算表
- **Type**: `rule`
- **Given**: 预估股价 10、成本价 10、股数 1000、单次 100、比例 1.017
- **When**: 点击计算
- **Then**: 生成阶梯表，各行价格/卖出额/剩余股数/累计收入/盈亏与 estimate-trade.js calculateEstimates 完全一致
- **Pass Condition**: 单元测试逐行断言前若干行与终止条件
- **Evidence**: JVM 单元测试输出

### AC-10: 费率设置联动
- **Type**: `rule`
- **Given**: 在"我的"页修改 A 股佣金率为 1.0‱
- **When**: 重启应用并发起一笔买入
- **Then**: 费率持久化生效，试算费用按新费率计算；过户费/印花税设置同样生效
- **Pass Condition**: 设置落盘且费用计算读取新值（单元测试 + 代码走查）
- **Evidence**: 单元测试 + 代码走查

### AC-11: 备份与小程序互通
- **Type**: `rule`
- **Given**: 小程序导出的一份 CSV 备份（含三段中文表头、双引号字段、BOM）
- **When**: 在 Android 版导入该文件并确认
- **Then**: 校验通过、条数统计正确、覆盖导入后资产/交易/设置与备份一致；Android 版导出的 CSV/JSON 能被同一解析逻辑读回（roundtrip），段标记与表头与 app.js 完全一致
- **Pass Condition**: 用小程序格式样例做解析单元测试 + CSV/JSON roundtrip 单元测试通过；导出通过 SAF 生成文件、导入通过 SAF 选文件
- **Evidence**: 单元测试（含小程序真实样例片段）+ 代码走查

### AC-12: UI 还原度
- **Type**: `rubric`
- **Dimension**: 界面与交互对小程序的还原程度（布局结构、卡片信息层级、主色 #1565C0、盈红亏绿、弹窗/确认/Toast 反馈、空状态）
- **Scale**: 1-5
- **Anchors**: 1 = 大量页面缺失或布局错乱；3 = 主要页面可用但视觉/交互与小程序差异明显；5 = 九页功能齐备、视觉与交互高度贴近小程序
- **Pass Threshold**: >= 4
- **Evidence**: 各 Screen Composable 代码走查与 APK 运行截图（如环境允许）

### AC-13: 计算口径忠实度
- **Type**: `rubric`
- **Dimension**: Kotlin 移植逻辑与 app.js 的忠实程度（费用、持仓回放、归档、利润、CSV 分段、预估阶梯）
- **Scale**: 1-5
- **Anchors**: 1 = 核心公式被改写导致数值不一致；3 = 主流程正确但边界（同日先买后卖、零股归零、归档转移、最低佣金）缺失；5 = 全部边界口径与 app.js 一致且有测试锁定
- **Pass Threshold**: >= 4
- **Evidence**: 对照 app.js 的代码走查 + 单元测试矩阵

## Open Questions
- 无（技术路线、构建方式、数据互通均已与用户确认；包名/应用名为假设项，可在审批时更正）。
