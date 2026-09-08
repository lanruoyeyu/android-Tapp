# T-account book Android 版 - 实施计划

> 说明：所有任务产物均位于 `e:\小程序\android-Tapp`。技术栈：Kotlin + Jetpack Compose + Material3 + Gson，单 Activity + Compose Navigation，minSdk 24 / compileSdk 35。业务逻辑对照 `E:\小程序\t-assetbook\app.js` 与各页面 js 逐行移植。

## Task 1: Android 工程骨架与构建环境
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 在 `e:\小程序\android-Tapp` 创建标准 Android Studio 工程：`settings.gradle.kts`、根 `build.gradle.kts`、`app/build.gradle.kts`、`gradle.properties`、`gradle/wrapper/gradle-wrapper.properties`、`gradlew.bat`/`gradlew`、`app/src/main/AndroidManifest.xml`。
  - 包名 `com.tassetbook.app`；应用名 "T-account book"；主题色 #1565C0；不申请 INTERNET 权限；minSdk 24、target/compile 35。
  - 依赖：androidx.core-ktx、activity-compose、compose-bom、material3、navigation-compose、lifecycle-viewmodel-compose、gson。
  - 下载安装 Android cmdline-tools（到工程外用户目录，如 `C:\Users\Admin\android-sdk`），用 sdkmanager 安装 platform-tools、platforms-35、build-tools，接受 licenses。
  - 验证 JDK 24 与 Gradle/AGP 版本组合（优先 Gradle 8.14+/AGP 8.7+ 兼容组合；若 JDK24 不被支持则调整 Gradle 版本至支持 JDK 24 的版本），使空 Compose 模板 `gradlew.bat assembleDebug` 可运行。
- **Acceptance Criteria Addressed**: AC-1, NFR-4, NFR-6
- **Test Requirements**:
  - `rule` TR-1.1: `gradlew.bat assembleDebug` 退出码 0 且产出 app-debug.apk（骨架阶段即可验证工具链）；证据：构建日志与 APK 路径。
  - `rule` TR-1.2: 工程文件全部位于 android-Tapp 目录内（SDK 可放用户目录），未修改小程序源码；证据：目录清单。

## Task 2: 数据模型、本地存储与仓库
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - Kotlin data class：`Asset`（含 baseTotalCost/baseShares/baseUnit/totalCost/shares/costPerShare/totalProfit/monthProfit/todayProfit/currentPrice/isTop/remark/createTime/updateTime 及展示用临时字段 buyPrices/sellPrices/allPrices/latestBuyPrice/latestSellPrice）、`Trade`（买卖两侧字段 + 兼容旧字段 price/shares/total/commission/transferFee/stampDuty + tradeStatus/profit/remark/tradeDate/tradeTime/isArchived/archiveTime/relatedBuy/relatedSell 等）、`Settings`（tradeRateA/HK/ETF、tradeRateTransfer、tradeRateStampDuty、defaultMarket/defaultExchange/theme）。
  - `LocalStore`：Gson 序列化到 filesDir 下 `assets.json`/`trades.json`/`settings.json`（对应 wx storage 三键）；启动加载、过滤无效项（asset.name 空、trade.assetId 空）、变更即落盘。
  - `AppRepository`（单例，StateFlow 暴露 assets/trades/settings）：承载 app.js 的 globalData 与全部方法（addAsset/updateAsset/deleteAsset、addTBuy/addTSell、archiveTrade/adjustBaseUnitAfterArchive、deleteTrade、getAssetTrades/getAllAssetTrades、calculateAssetProfit、getStatistics、replayPosition 持仓回放、saveData）。
  - 工具：`MoneyUtils`（round 到 N 位、千分位格式化、‱ 费率换算）、`DateUtils`（兼容 `yyyy/M/d`、`yyyy-M-d`、ISO 的宽松解析与格式化）。
- **Acceptance Criteria Addressed**: AC-2, AC-12, AC-13, NFR-2
- **Test Requirements**:
  - `rule` TR-2.1: addAsset 后字段（id、baseUnit=总成本/股数、costPerShare 6 位精度）与 app.js addAsset 一致；证据：单元测试。
  - `rule` TR-2.2: 数据写入 filesDir 后重新加载内容一致；证据：单元测试（临时目录）。

## Task 3: 核心领域逻辑移植与单元测试（费用/持仓/归档/利润/预估/CSV）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 2
- **Description**:
  - 对照 `app.js` 与 `trade-operation.js` 实现并锁定：
    - 费用：佣金 max(金额×率,5)、过户费、印花税（仅卖），2 位小数；费率 ‱/10000。
    - addTBuy / addTSell（含股数不足拦截、净额扣成本、做T利润）。
    - 补卖(buyNoSell→buyToSell)、补买(sellNoBuy→sellToBuy)、编辑买入/卖出/T完成（撤旧值+应用新值）七分支。
    - 持仓回放：底仓起、日期升序、同日先买后卖、买加卖减、归零保护、成本价 3 位。
    - archiveTrade + adjustBaseUnitAfterArchive（净影响转入底仓，归档前后成本价不变）。
    - calculateAssetProfit（总/本月/今日，利润归属日期取买卖较晚者）、getStatistics。
    - estimate ladder（estimate-trade.js calculateEstimates 公式与终止条件）。
    - jsonToCsv（三段：资产 15 列、交易 30 列、交易明细表）、csvToJson/parseCsvLine/convertRowToObject、importData（覆盖式）、JSON 导出 v2.0.0。
  - JVM 单元测试（src/test）：AC-3/AC-4/AC-5/AC-6/AC-9/AC-11 的数值断言；用小程序真实字段构造样例；CSV 样例 roundtrip（含 BOM、引号字段、中文表头）。
- **Acceptance Criteria Addressed**: AC-3, AC-4, AC-5, AC-6, AC-9, AC-11, AC-13, NFR-2
- **Test Requirements**:
  - `rule` TR-3.1: AC-3/AC-4 场景费用与持仓数值逐笔断言通过；证据：测试报告。
  - `rule` TR-3.2: 归档前后 totalCost/shares/costPerShare 不变；证据：测试报告。
  - `rule` TR-3.3: 预估表前 3 行与终止行数值与 JS 公式一致；证据：测试报告。
  - `rule` TR-3.4: CSV 导出文本包含 `===资产数据===`/`===交易记录===`/`===交易明细表===` 且表头列数/列名与 app.js 一致；导出再导入 roundtrip 资产/交易条数与字段一致；证据：测试报告。

## Task 4: 主导航骨架与主题
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 2
- **Description**:
  - `MainActivity` + Compose Navigation：底部三 Tab（股票/统计/我的），二级路由 add-asset、trade-operation、trade-record、estimate-trade、export-config（参数通过 nav arguments 传递 assetId/assetName/tradeStatus/tradeId/source/isEdit）。
  - Material3 主题：primary #1565C0、买/正T 蓝、卖/反T 橙、盈利红 #f5222d 系、亏损绿 #52c41a 系（以小程序 wxss 实际配色为准核对）；通用组件：确认对话框、Toast（Snackbar）、加载态、空状态、卡片、数字输入框（decimal）、日期选择器（DatePickerDialog）。
- **Acceptance Criteria Addressed**: AC-12, FR-1
- **Test Requirements**:
  - `rule` TR-4.1: 三 Tab 与各二级页路由可跳转/返回，参数正确传递；证据：代码走查 + 编译通过。
  - `rubric` TR-4.2: 视觉一致性；1-5；1=主题色/组件混乱，3=可用但不贴近，5=与小程序视觉一致；阈值 >=4；证据：代码走查/截图。

## Task 5: 股票首页（AssetsScreen）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 3, Task 4
- **Description**:
  - 对照 `pages/assets`：顶部标题"股票管理"、搜索框（按名称过滤）、"+添加"；资产卡片（名称、市场/交易所、总持仓成本、股数、成本价、最近买卖价）、按钮组（正T买入/反T卖出/预估交易/展开收起）、卡内入口（交易记录、编辑）、展开区价格梯（买 I/卖 O 标记，`price[shares]`）；空状态；删除确认；置顶切换；进入页面 onShow 重算（调用 repository 回放 + calculateAssetProfit）。
- **Acceptance Criteria Addressed**: AC-2, AC-12, FR-2
- **Test Requirements**:
  - `rule` TR-5.1: 卡片展示的 totalCost/shares/costPerShare 来自回放结果且与单元测试口径一致；搜索/删除/置顶生效；证据：代码走查。
  - `rubric` TR-5.2: 列表还原度 1-5（布局/按钮/价格梯/空状态）；阈值 >=4；证据：代码走查/截图。

## Task 6: 添加/编辑资产页（AddAssetScreen）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 4
- **Description**:
  - 对照 `pages/add-asset`：名称输入；市场选择（A股/港股/ETF）与交易所联动（A股→沪市/深市，港股→港交所，ETF→沪市/深市/港交所）；底仓股数、底仓单价、底仓总成本三者联动（股数×单价=总成本，保留 2 位）；允许零底仓；编辑模式预填、可删除（确认）；保存后返回。
- **Acceptance Criteria Addressed**: AC-2, FR-2
- **Test Requirements**:
  - `rule` TR-6.1: 股数×单价联动总成本；编辑保存后 repository 数据更新且 baseUnit 重算；证据：代码走查 + 单元测试。

## Task 7: 交易操作页（TradeOperationScreen）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 3, Task 4
- **Description**:
  - 对照 `pages/trade-operation`：按入口参数（assetId/assetName/tradeId/source/tradeStatus/isEdit）渲染 7 种形态：新建正T买入、新建反T卖出、补卖（显示原买入、卖出股数预填）、补买（显示原卖出、买入股数预填）、编辑买入、编辑卖出、编辑 T完成。
  - 输入价格/股数实时试算：佣金、过户费、印花税（卖）、手续费合计、成交金额、买入总额/卖出净额；日期选择（默认今天，编辑取原日期）；备注；提交按钮可用性校验；成功/失败 Toast 后返回。
  - 标题逻辑 getPageTitle 与小程序一致。
- **Acceptance Criteria Addressed**: AC-3, AC-4, AC-5, AC-10, FR-3
- **Test Requirements**:
  - `rule` TR-7.1: 7 种形态提交后 repository 状态变化与 trade-operation.js 各分支一致（复用 Task 3 测试）；股数不足/空价拦截；证据：单元测试 + 代码走查。
  - `rule` TR-7.2: 试算区数值与 _calcFees/calculateBuyFees/calculateSellFees 一致；证据：单元测试。

## Task 8: 交易记录页（TradeRecordScreen）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 7
- **Description**:
  - 对照 `pages/trade-record`：顶部资产持仓（股数/总成本/成本价）与总/本月/今日利润；筛选 Tab（未T完/T完成/归档）；排序切换（完成时间/买入价，未T完按价格降序）；记录卡片：状态徽章、买/卖价格股数、费用明细展开（佣金/过户费/印花税/手续费/总额/日期）、单笔利润；操作：编辑、补买/补卖（按状态跳转 TradeOperation）、归档（确认）、删除（确认）；清空该资产全部交易（确认后持仓归零）。
- **Acceptance Criteria Addressed**: AC-6, AC-7, FR-4
- **Test Requirements**:
  - `rule` TR-8.1: 三种筛选集合与排序规则与 trade-record.js 一致；利润汇总口径一致；清空后持仓归零；证据：单元测试 + 代码走查。

## Task 9: 统计页（StatisticsScreen）
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 3, Task 4
- **Description**:
  - 对照 `pages/statistics`：汇总卡（总/本月/今日做T利润）；每只股票当月成本价折线图（Compose Canvas 手绘：按当月每日回放成本价，Y 轴刻度，触摸/点击显示 tooltip：日期/股数/成本价/总成本，含该股票总收益与收益率）；利润日历（月份切换、周日~周六网格、当日盈亏文字盈红亏绿、今天高亮、未来日期置灰、点选日期展示当日分股票收益明细与合计）。
- **Acceptance Criteria Addressed**: AC-8, FR-5
- **Test Requirements**:
  - `rule` TR-9.1: 当月每日成本价序列与 statistics.js 回放逻辑一致（抽 3 个日期断言）；日历当日盈亏 = 当日完成 T 的利润合计；证据：单元测试。
  - `rubric` TR-9.2: 图表/日历可读性与还原度 1-5；阈值 >=4；证据：代码走查/截图。

## Task 10: 预估交易页（EstimateTradeScreen）
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 3, Task 4
- **Description**:
  - 对照 `pages/estimate-trade`：表单（预估股价、成本价[带入资产]、总股数[带入]、总成本[联动]、单次卖出股数、阶梯比例% 默认 1.017）；计算按钮；结果 LazyColumn 最多 100 行（序号/价格/卖出额/剩余股数/总价/累计收入/盈亏，盈亏着色）。
- **Acceptance Criteria Addressed**: AC-9, FR-6
- **Test Requirements**:
  - `rule` TR-10.1: 结果表与 Task 3 预估测试一致；证据：单元测试 + 代码走查。

## Task 11: 我的页与费率设置（MineScreen）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 4
- **Description**:
  - 对照 `pages/mine`：头部（图标 + "专业做T记账工具"）；统计（资产数、交易数、买/卖笔数等）；功能项：数据导出（→ ExportConfig）、数据导入（SAF 选文件）、通用交易费率展开子菜单（A股/港股/ETF 佣金 ‱ 内联编辑-设置/✓确认，落盘 settings）、清空所有数据（两次确认）、关于（弹窗含版本与说明）；底部版本号。
- **Acceptance Criteria Addressed**: AC-10, FR-7
- **Test Requirements**:
  - `rule` TR-11.1: 费率修改后 settings 落盘且 repository 费用计算读取新值；清空数据后 assets/trades 为空并落盘；证据：单元测试 + 代码走查。

## Task 12: 备份导出/导入（ExportConfigScreen + SAF）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 3, Task 11
- **Description**:
  - 对照 `pages/export-config` 与 `mine.onImportData`：
    - 导出：格式 CSV/JSON、范围 全部/仅资产/仅交易、时间范围 全部/今日/本周/本月/自定义（起止日期）；预览前 20 行与总行数；`ACTION_CREATE_DOCUMENT` 保存（CSV 加 BOM，文件名 `tassetbook_export_yyyyMMdd.csv|json`），成功提示。
    - 导入：`ACTION_OPEN_DOCUMENT` 选 .csv/.txt/.json；UTF-8 读取去 BOM；校验段标记（CSV 需含 `===资产数据===`/`===交易记录===`，JSON 需可解析含 assets/trades）；统计条数与文件信息后二次确认；覆盖式导入并报告结果；失败给出原因弹窗。
- **Acceptance Criteria Addressed**: AC-11, FR-8, FR-9
- **Test Requirements**:
  - `rule` TR-12.1: 导出 CSV 文本与 app.js jsonToCsv 分段/表头一致（Task 3 测试覆盖）；SAF 写入/读取链路在代码中完整（CreateDocument/OpenDocument + contentResolver 流）；证据：单元测试 + 代码走查。
  - `rule` TR-12.2: 导入小程序格式样例成功、非法文件被拦截并提示；证据：单元测试（解析层）+ 代码走查。

## Task 13: 整体构建、修复与 APK 产出
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 5, Task 6, Task 7, Task 8, Task 9, Task 10, Task 11, Task 12
- **Description**:
  - 全量 `gradlew.bat assembleDebug`；修复所有编译/lint 阻断问题；安装到模拟器/真机如可用则冒烟（无设备则以编译通过 + 单元测试通过为准）。
  - 产出 `app/build/outputs/apk/debug/app-debug.apk`，输出安装说明。
- **Acceptance Criteria Addressed**: AC-1
- **Test Requirements**:
  - `rule` TR-13.1: assembleDebug 退出码 0、APK 存在；证据：构建日志 + APK 路径/大小。
  - `rule` TR-13.2: `gradlew.bat testDebugUnitTest` 全部通过；证据：测试报告。
