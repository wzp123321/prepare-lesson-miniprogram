-- ============================================================
-- 备课排课管理系统 —— 示例数据（seed）
-- 用途：一键铺满各页面（今日待办 / 排课网格 / 待备课 / 题库 / 试卷 / 制卷台 / 看板）
-- 特性：**幂等可重复执行** —— 学生/课程按姓名判重、课节按(日期,时段)判重、
--       题目按题干判重、试卷按标题判重，重复跑不会产生脏数据。
-- 前置：先执行 schema.sql；库名 lesson_db。
--   export MYSQL_PWD=123456
--   mysql -h127.0.0.1 -uroot -Dlesson_db < backend/db/seed.sql
-- 说明：所有造出来的数据 source / remark 标注为「示例数据」，便于日后清理。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 学生（现有「张三」保留；补 5 名跨 3~6 年级）
-- ------------------------------------------------------------
INSERT INTO student (name, grade, phone, address, price, status, color, remark)
SELECT * FROM (
  SELECT '张三'   AS name, '二年级' AS grade, '' AS phone, '' AS address, 555.00 AS price, 1 AS status, '#409EFF' AS color, '' AS remark
  UNION ALL SELECT '李思远', '三年级', '', '', 600.00, 1, '#67C23A', ''
  UNION ALL SELECT '王雨桐', '四年级', '', '', 650.00, 1, '#E6A23C', '作文薄弱，重点练习作'
  UNION ALL SELECT '赵梓涵', '五年级', '', '', 700.00, 1, '#F56C6C', ''
  UNION ALL SELECT '陈梦豪', '六年级', '', '', 750.00, 1, '#909399', ''
  UNION ALL SELECT '刘一诺', '六年级', '', '', 750.00, 1, '#9C27B0', ''
) t
WHERE NOT EXISTS (SELECT 1 FROM student s WHERE s.name = t.name);

-- ------------------------------------------------------------
-- 2. 课程（每生一门语文课，价格与学生对齐）
-- ------------------------------------------------------------
INSERT INTO course (student_id, subject, price, remark, enabled)
SELECT s.id, '语文', s.price, '', 1
FROM student s
WHERE NOT EXISTS (SELECT 1 FROM course c WHERE c.student_id = s.id);

-- ------------------------------------------------------------
-- 3. 时间段（网格两列；缺失才补）
-- ------------------------------------------------------------
INSERT INTO time_slot (start_time, end_time, sort_order, enabled)
SELECT * FROM (
  SELECT CAST('09:00:00' AS TIME) AS st, CAST('10:30:00' AS TIME) AS et, 1 AS so, 1 AS en
  UNION ALL SELECT CAST('13:00:00' AS TIME), CAST('15:00:00' AS TIME), 2, 1
) t
WHERE NOT EXISTS (SELECT 1 FROM time_slot x WHERE x.start_time = t.st);

-- ------------------------------------------------------------
-- 4. 10 月整月排课
--    硬约束：uk_lesson_date_slot 唯一 → 同一天同一时段只能 1 人。
--    两个时段 × 每天 → 每天最多排 2 人；6 人分 3 组，各组占不同星期，
--    组内两人分占 slot1 / slot2，保证任何一天都不撞车：
--      组A（第 1、2 位）：周一/周四    slot1、slot2
--      组B（第 3、4 位）：周二/周五    slot1、slot2
--      组C（第 5、6 位）：周三/周六    slot1、slot2
--    状态：10/10 之前 = NORMAL（已上）；10/10 slot1 = NORMAL，其余 = UNTAKEN
--    ⚠️ 全部按 student.name 关联，不依赖自增 id（库非空时 id 不从 1 开始）。
-- ------------------------------------------------------------
INSERT INTO lesson (student_id, course_id, slot_id, lesson_date, status, closed)
WITH RECURSIVE days(d) AS (
  SELECT DATE('2026-10-01')
  UNION ALL SELECT DATE_ADD(d, INTERVAL 1 DAY) FROM days WHERE d < DATE('2026-10-31')
),
stu AS (
  -- 稳定编号 0..5（按姓名），天然决定组与时段
  SELECT name,
         ROW_NUMBER() OVER (ORDER BY FIELD(name,'张三','李思远','王雨桐','赵梓涵','陈梦豪','刘一诺')) - 1 AS n
  FROM student
),
plan AS (
  SELECT name,
         -- 组内第 1 人 slot1、第 2 人 slot2（同一天两时段各一人，不撞）
         (n % 2) + 1 AS slot,
         -- 组 A(0,1)→周一/周四；组 B(2,3)→周二/周五；组 C(4,5)→周三/周六
         d
  FROM stu JOIN days
  WHERE (n < 2 AND DAYOFWEEK(d) IN (2, 5))    -- 周一(2)、周四(5)
     OR (n IN (2,3) AND DAYOFWEEK(d) IN (3, 6)) -- 周二(3)、周五(6)
     OR (n >= 4 AND DAYOFWEEK(d) IN (4, 7))     -- 周三(4)、周六(7)
)
SELECT s.id, c.id, p.slot, p.d,
       CASE WHEN p.d < '2026-10-10' THEN 'NORMAL'
            WHEN p.d = '2026-10-10' AND p.slot = 1 THEN 'NORMAL'
            ELSE 'UNTAKEN' END,
       0
FROM plan p
JOIN student s ON s.name = p.name
JOIN course c ON c.student_id = s.id
WHERE NOT EXISTS (
  SELECT 1 FROM lesson l WHERE l.lesson_date = p.d AND l.slot_id = p.slot
);

-- ------------------------------------------------------------
-- 5. 请假 / 补课场景（四态齐全，供待办与看板演示）
--    ⚠️ 一律按学生姓名定位，不依赖自增 id
-- ------------------------------------------------------------
-- 张三 10/08 病假 → 未补（逾期）
UPDATE lesson l JOIN student s ON s.id = l.student_id
   SET l.status='ABSENT', l.absent_by='student', l.absent_reason='学生病假', l.closed=0
 WHERE s.name='张三' AND l.lesson_date='2026-10-08';
-- 赵梓涵 10/06 法定节假日 → 未补（逾期）
UPDATE lesson l JOIN student s ON s.id = l.student_id
   SET l.status='ABSENT', l.absent_by='student', l.absent_reason='法定节假日', l.closed=0
 WHERE s.name='赵梓涵' AND l.lesson_date='2026-10-06';
-- 王雨桐 10/09 老师请假 → 已补 10/11
UPDATE lesson l JOIN student s ON s.id = l.student_id
   SET l.status='MADEUP', l.absent_by='teacher', l.absent_reason='老师请假', l.make_up_date='2026-10-11', l.closed=1
 WHERE s.name='王雨桐' AND l.lesson_date='2026-10-09';
-- 李思远 10/08 事假 → 已补 10/10
UPDATE lesson l JOIN student s ON s.id = l.student_id
   SET l.status='MADEUP', l.absent_by='student', l.absent_reason='学生事假', l.make_up_date='2026-10-10', l.closed=1
 WHERE s.name='李思远' AND l.lesson_date='2026-10-08';

-- ------------------------------------------------------------
-- 6. 备课备注（prep_remark）与排课备注（remark）
--    ⚠️ 两者是不同列，别混用；按姓名定位
--    ⚠️ 日期必须落在该生实际有课的星期：
--       组A（张三/李思远）周一·周四　组B（王雨桐/赵梓涵）周二·周五　组C（陈梦豪/刘一诺）周三·周六
-- ------------------------------------------------------------
-- 组A
UPDATE lesson l JOIN student s ON s.id=l.student_id
   SET l.prep_remark='复习修辞（比喻/拟人），配合例句辨析', l.remark='这次课讲得偏快，下次放慢'
 WHERE s.name='张三' AND l.lesson_date='2026-10-05';
UPDATE lesson l JOIN student s ON s.id=l.student_id
   SET l.prep_remark='看图写话：先口头描述再落笔'
 WHERE s.name='张三' AND l.lesson_date='2026-10-12';          -- 下周一（待备）
UPDATE lesson l JOIN student s ON s.id=l.student_id
   SET l.prep_remark='说明方法辨析（举例子/列数字/打比方）'
 WHERE s.name='李思远' AND l.lesson_date='2026-10-12';        -- 下周一（待备）
-- 组B
UPDATE lesson l JOIN student s ON s.id=l.student_id
   SET l.prep_remark='记叙文概括主要内容，三要素入手', l.remark='学生状态好，思维活跃'
 WHERE s.name='王雨桐' AND l.lesson_date='2026-10-06';
UPDATE lesson l JOIN student s ON s.id=l.student_id
   SET l.prep_remark='成语积累 + 近义反义词对比'
 WHERE s.name='赵梓涵' AND l.lesson_date='2026-10-09';
-- 组C（今天 10-10 是周六，正好是他们的课）
UPDATE lesson l JOIN student s ON s.id=l.student_id
   SET l.prep_remark='文言实词：一词多义梳理'
 WHERE s.name='刘一诺' AND l.lesson_date='2026-10-10';
UPDATE lesson l JOIN student s ON s.id=l.student_id
   SET l.remark='本次默写全对，表扬'
 WHERE s.name='陈梦豪' AND l.lesson_date='2026-10-10';

-- ------------------------------------------------------------
-- 7. 课次-知识点关联（lesson_knowledge，本节讲什么）—— 按姓名 + 实际有课日期
-- ------------------------------------------------------------
INSERT INTO lesson_knowledge (lesson_id, kp_id)
SELECT l.id, 16 FROM lesson l JOIN student s ON s.id=l.student_id
 WHERE s.name='张三' AND l.lesson_date='2026-10-05'   -- 修辞（比喻拟人）
  AND NOT EXISTS (SELECT 1 FROM lesson_knowledge k WHERE k.lesson_id=l.id AND k.kp_id=16);
INSERT INTO lesson_knowledge (lesson_id, kp_id)
SELECT l.id, 12 FROM lesson l JOIN student s ON s.id=l.student_id
 WHERE s.name='张三' AND l.lesson_date='2026-10-12'   -- 看图写话（下节待备）
  AND NOT EXISTS (SELECT 1 FROM lesson_knowledge k WHERE k.lesson_id=l.id AND k.kp_id=12);
INSERT INTO lesson_knowledge (lesson_id, kp_id)
SELECT l.id, 23 FROM lesson l JOIN student s ON s.id=l.student_id
 WHERE s.name='李思远' AND l.lesson_date='2026-10-12'  -- 说明方法（举例子列数字）
  AND NOT EXISTS (SELECT 1 FROM lesson_knowledge k WHERE k.lesson_id=l.id AND k.kp_id=23);
INSERT INTO lesson_knowledge (lesson_id, kp_id)
SELECT l.id, 17 FROM lesson l JOIN student s ON s.id=l.student_id
 WHERE s.name='王雨桐' AND l.lesson_date='2026-10-06'  -- 概括段意
  AND NOT EXISTS (SELECT 1 FROM lesson_knowledge k WHERE k.lesson_id=l.id AND k.kp_id=17);
INSERT INTO lesson_knowledge (lesson_id, kp_id)
SELECT l.id, 13 FROM lesson l JOIN student s ON s.id=l.student_id
 WHERE s.name='赵梓涵' AND l.lesson_date='2026-10-09'  -- 成语积累
  AND NOT EXISTS (SELECT 1 FROM lesson_knowledge k WHERE k.lesson_id=l.id AND k.kp_id=13);
INSERT INTO lesson_knowledge (lesson_id, kp_id)
SELECT l.id, 32 FROM lesson l JOIN student s ON s.id=l.student_id
 WHERE s.name='刘一诺' AND l.lesson_date='2026-10-10'  -- 文言实词（今天待备）
  AND NOT EXISTS (SELECT 1 FROM lesson_knowledge k WHERE k.lesson_id=l.id AND k.kp_id=32);

-- ------------------------------------------------------------
-- 8. 题库（52 题，覆盖 1~6 年级 / 字词·句子·阅读·古诗文·写作·基础）
--    按题干判重，重复执行不翻倍；kpId 取自 knowledge_point 既有种子。
-- ------------------------------------------------------------
INSERT INTO question (grade, kp_id, qtype, stem, options, answer, analysis, difficulty, source)
SELECT * FROM (
  -- ===== 二年级 =====
  SELECT '二年级' g, 7 kp, '选择' t, '下列加点字读音完全正确的一项是（　）' s,
         '["A. 银行（xíng）","B. 行业（háng）","C. 行走（háng）"]' o, 'B' a,
         '「银行」读 yín háng，「行业」读 háng yè，「行走」读 xíng zǒu。多音字要结合词义判断。' an, 2 d, '示例数据' src
  UNION ALL SELECT '二年级', 7, '填空', '给加点字选择正确读音：\n长（　）短不一　　cháng / zhǎng', NULL, 'cháng', '表示「长度」义时读 cháng，如「长短」；表示「生长」义时读 zhǎng，如「长大」。', 1, '示例数据'
  UNION ALL SELECT '二年级', 8, '选择', '下列词语中没有错别字的一项是（　）', '["A. 已经","B. 以经","C. 己经"]', 'A', '「已经」表示时间过去；「以」与「己」形近，注意区分。', 1, '示例数据'
  UNION ALL SELECT '二年级', 8, '判断', '「玩要」是「玩耍」的正确写法。', NULL, '错误', '「玩耍」的「耍」不要写成「要」。', 1, '示例数据'
  UNION ALL SELECT '二年级', 9, '填空', '写出下列词语的近义词：\n高兴 —— ______', NULL, '快乐', '「高兴」与「快乐」都表示心情愉悦，是常见近义词。', 1, '示例数据'
  UNION ALL SELECT '二年级', 9, '选择', '下列不是「寒冷」的反义词的一项是（　）', '["A. 温暖","B. 炎热","C. 冰凉"]', 'C', '「寒冷」的反义是「温暖」「炎热」；「冰凉」与「寒冷」意思相近。', 2, '示例数据'
  UNION ALL SELECT '二年级', 10, '选择', '下列句子标点使用正确的一项是（　）', '["A. 今天天气真好啊。","B. 今天天气真好啊！","C. 今天天气真好啊，"]', 'B', '感叹句句末用感叹号，表达强烈语气。', 1, '示例数据'
  UNION ALL SELECT '二年级', 10, '填空', '给句子加上合适的标点：\n你今天怎么没来（　）', NULL, '？', '疑问句句末用问号。', 1, '示例数据'
  UNION ALL SELECT '二年级', 11, '填空', '把句子改成「把」字句：\n小明吃掉了那块蛋糕。\n→ ______', NULL, '小明把那块蛋糕吃掉了。', '「把」字句结构：谁 + 把 + 什么 + 怎么样。', 2, '示例数据'
  UNION ALL SELECT '二年级', 12, '写作', '看图写话：图上是一个小朋友在雨中给同学撑伞。请观察图画，写一段通顺的话，注意写清时间、地点、人物和事情。', NULL, '（略）', '先整体看图，再按「什么时间、什么地方、谁、在做什么」的顺序写，最后写出感受。', 2, '示例数据'
  -- ===== 三年级 =====
  UNION ALL SELECT '三年级', 13, '填空', '补全成语：\n一（　）反三　　专心（　）志', NULL, '举；致', '「举一反三」「专心致志」是三年级常见成语。', 1, '示例数据'
  UNION ALL SELECT '三年级', 13, '选择', '下列成语使用恰当的一项是（　）', '["A. 他学习很马虎，真是全神贯注。","B. 同学们专心致志地听讲。","C. 他做事三心二意，非常认真。"]', 'B', '成语要符合语境，「专心致志」形容非常专心，与「听讲」搭配恰当。', 2, '示例数据'
  UNION ALL SELECT '三年级', 14, '选择', '选择合适的关联词填空：\n（　）明天下雨，我们（　）不去公园了。', '["A. 因为……所以……","B. 如果……就……","C. 虽然……但是……"]', 'B', '前后是假设关系，用「如果……就……」最恰当。', 2, '示例数据'
  UNION ALL SELECT '三年级', 15, '填空', '修改病句：\n通过学习，使我懂得了许多道理。\n→ ______', NULL, '通过学习，我懂得了许多道理。', '「通过」和「使」连用导致主语残缺，去掉其中一个即可。', 3, '示例数据'
  UNION ALL SELECT '三年级', 15, '选择', '下列句子没有语病的一项是（　）', '["A. 我们要养成讲卫生的好风气。","B. 我们要养成讲卫生的好习惯。","C. 我们要养成讲卫生的好方法。"]', 'B', '「养成」与「习惯」搭配，「风气」「方法」搭配不当。', 2, '示例数据'
  UNION ALL SELECT '三年级', 16, '选择', '「弯弯的月亮像一只小船」运用的修辞手法是（　）', '["A. 拟人","B. 比喻","C. 夸张"]', 'B', '把「月亮」比作「小船」，本体与喻体相似，是比喻。', 1, '示例数据'
  UNION ALL SELECT '三年级', 16, '填空', '判断修辞手法：\n「小鸟在枝头快乐地唱歌。」　这是______句', NULL, '拟人', '把小鸟当作人来写（会「唱歌」「快乐」），是拟人。', 2, '示例数据'
  UNION ALL SELECT '三年级', 17, '阅读', '读下面的段落，概括段意：\n秋天的果园里，苹果红了，梨子黄了，葡萄像一串串珍珠。农民们忙着采摘，脸上洋溢着丰收的喜悦。\n请用一句话概括这段话的主要意思。', NULL, '秋天的果园果实累累，农民们喜获丰收。', '概括段意要抓住「谁 + 怎么样」，去掉细节，保留主干。', 2, '示例数据'
  UNION ALL SELECT '三年级', 18, '阅读', '阅读短文《小院的四季》，回答问题：\n春天，小院里的桃花开了；夏天，葡萄架上挂满了果实；秋天，菊花争奇斗艳；冬天，梅花在雪中绽放。\n短文是按什么顺序写的？', NULL, '时间顺序（四季）', '按「春、夏、秋、冬」排列，是典型的时间顺序。', 2, '示例数据'
  UNION ALL SELECT '三年级', 19, '写作', '习作：写一个你熟悉的人。\n要求：①通过一两件具体的事表现人物的特点；②语句通顺，不少于 300 字。', NULL, '（略）', '写人记事要紧扣人物特点选材，用具体事例支撑，注意细节描写。', 2, '示例数据'
  -- ===== 四年级 =====
  UNION ALL SELECT '四年级', 20, '选择', '「他的声音大得能把房顶掀翻」运用的修辞手法是（　）', '["A. 比喻","B. 夸张","C. 排比"]', 'B', '「能把房顶掀翻」是对声音大的夸大，属于夸张。', 1, '示例数据'
  UNION ALL SELECT '四年级', 20, '填空', '写出运用排比手法的一句话（至少三句并列）：\n______', NULL, '（示例）春天来了，花儿红了，草儿绿了，鸟儿唱了。', '排比要求三个或以上结构相似的句子。', 2, '示例数据'
  UNION ALL SELECT '四年级', 21, '填空', '修改病句：\n他的家乡是江苏人。\n→ ______', NULL, '他的家乡是江苏。（或：他是江苏人。）', '主语「家乡」与宾语「人」搭配不当。', 2, '示例数据'
  UNION ALL SELECT '四年级', 22, '填空', '把下面的句子改成反问句：\n这比山还高比海还深的情谊，我们不会忘记。\n→ ______', NULL, '这比山还高比海还深的情谊，我们怎么会忘记呢？', '陈述句改反问句：加反问词「怎么……呢」，肯否互换，句末用问号。', 3, '示例数据'
  UNION ALL SELECT '四年级', 23, '选择', '「松鼠的尾巴像一把降落伞」这句话运用的说明方法是（　）', '["A. 举例子","B. 打比方","C. 列数字"]', 'B', '把尾巴比作降落伞，是打比方（比喻）的说明方法。', 2, '示例数据'
  UNION ALL SELECT '四年级', 23, '填空', '「这座桥长约 50 米，宽约 8 米。」这句话运用的说明方法是______。', NULL, '列数字', '用具体数字说明桥的长宽，是列数字。', 1, '示例数据'
  UNION ALL SELECT '四年级', 24, '阅读', '阅读短文《詹天佑》（节选），概括主要内容：\n詹天佑主持修筑京张铁路，克服了重重困难，提前两年竣工，为中国人争了光。\n请概括这段文字的主要内容。', NULL, '詹天佑主持修筑京张铁路，克服困难提前竣工，为国争光。', '概括主要内容可抓「谁 + 做了什么 + 结果如何」。', 2, '示例数据'
  UNION ALL SELECT '四年级', 25, '阅读', '阅读诗歌《乡愁》（节选），说说表达了作者怎样的思想感情。\n小时候／乡愁是一枚小小的邮票／我在这头／母亲在那头……', NULL, '表达了作者对故乡、对亲人的深切思念之情。', '体会思想感情要从意象（邮票、母亲）和反复的句式入手。', 3, '示例数据'
  UNION ALL SELECT '四年级', 26, '写作', '习作：写一处你喜欢的景物。\n要求：抓住景物的特点，按照一定的顺序描写，做到情景交融，不少于 350 字。', NULL, '（略）', '写景要按顺序（如远近、上下、四季），抓住颜色、形状、声音等特点。', 2, '示例数据'
  -- ===== 五年级 =====
  UNION ALL SELECT '五年级', 27, '填空', '修改病句：\n大家讨论并听取了校长的报告。\n→ ______', NULL, '大家听取并讨论了校长的报告。', '应先「听取」后「讨论」，原句语序颠倒。', 2, '示例数据'
  UNION ALL SELECT '五年级', 28, '选择', '「地球的表面积约 5.1 亿平方公里，其中海洋约占 71%。」这句话运用的说明方法是（　）', '["A. 打比方","B. 作比较","C. 列数字"]', 'C', '用具体数据说明，是列数字。', 1, '示例数据'
  UNION ALL SELECT '五年级', 28, '填空', '「鲸的一条舌头就有十几头大肥猪那么重。」运用的说明方法是______。', NULL, '作比较', '用「大肥猪」的重量与鲸舌比较，突出其重，是作比较。', 2, '示例数据'
  UNION ALL SELECT '五年级', 29, '阅读', '阅读《人物描写一组》（节选），分析文中人物形象：\n他个子不高，脸上总是带着笑。看到同学摔倒，他立刻跑过去扶起，还细心地拍掉对方身上的土。\n请概括这个人物的形象特点。', NULL, '他善良、热心、细心，乐于助人。', '分析人物形象要结合具体言行，从「扶起」「拍土」等细节概括品质。', 2, '示例数据'
  UNION ALL SELECT '五年级', 30, '阅读', '阅读短文，说说文中环境描写的作用：\n天阴沉沉的，风呼呼地刮着，路上的行人裹紧了大衣。他站在校门口，等着妈妈来接他。', NULL, '渲染了寒冷、压抑的气氛，衬托出他等待时的心情。', '环境描写常用来渲染气氛、烘托心情、推动情节。', 3, '示例数据'
  UNION ALL SELECT '五年级', 31, '古诗文', '阅读古诗《泊船瓜洲》，回答问题：\n京口瓜洲一水间，钟山只隔数重山。\n春风又绿江南岸，明月何时照我还？\n「绿」字用得妙在哪里？', NULL, '「绿」字把春风写活了，写出了春风的生机与动态美，比「到」「过」等字更形象。', '「绿」是形容词作动词，化静为动，富有表现力。', 3, '示例数据'
  UNION ALL SELECT '五年级', 32, '填空', '解释加点词的意思：\n「停车坐爱枫林晚」中的「坐」：______', NULL, '因为', '「坐」在古诗文中常作「因为」讲。', 2, '示例数据'
  UNION ALL SELECT '五年级', 33, '写作', '习作：以「成长中的一件事」为题，写一篇记叙文。\n要求：中心明确，选材典型，详略得当，不少于 400 字。', NULL, '（略）', '审题要抓住「成长」这一中心，选取对自己有触动的事，写出变化与感悟。', 2, '示例数据'
  -- ===== 六年级 =====
  UNION ALL SELECT '六年级', 34, '阅读', '阅读《草原》（节选），回答问题：\n作者先写草原的景色，再写蒙古族人民的热情好客。\n短文采用了什么样的记叙顺序？', NULL, '由景及人（事情发展的先后顺序）。', '先写景后写人，属于按事情发展或空间转换的顺序。', 2, '示例数据'
  UNION ALL SELECT '六年级', 35, '阅读', '阅读短文《桥》，回答问题：\n文章以「桥」为题，却写了一位老支书。\n这个标题有什么作用？', NULL, '一语双关，既指洪水中那座木桥，又象征老支书舍己为人的精神，点明中心、引发思考。', '标题作用常从「线索、象征、点题、吸引读者」几方面回答。', 3, '示例数据'
  UNION ALL SELECT '六年级', 36, '阅读', '阅读短文，找出文中的过渡句：\n春天，百花盛开。夏天，绿树成荫。\n秋天到了，天气渐渐转凉，树叶也慢慢变黄了。\n冬天，北风呼啸，大雪纷飞。\n哪一句起过渡作用？', NULL, '「秋天到了，天气渐渐转凉，树叶也慢慢变黄了。」', '过渡句承上启下，把前后内容自然衔接。', 3, '示例数据'
  UNION ALL SELECT '六年级', 37, '古诗文', '翻译句子：\n「虽与之俱学，弗若之矣。」', NULL, '虽然与他一起学习，成绩却不如他。', '重点词：俱（一起）、弗（不）、若（如、比得上）。', 3, '示例数据'
  UNION ALL SELECT '六年级', 38, '填空', '默写：\n「千里莺啼绿映红，______。」', NULL, '水村山郭酒旗风', '出自杜牧《江南春》。', 2, '示例数据'
  UNION ALL SELECT '六年级', 39, '选择', '《西游记》中，孙悟空的兵器是（　）', '["A. 金箍棒","B. 方天画戟","C. 九齿钉耙"]', 'A', '金箍棒又名如意金箍棒；九齿钉耙是猪八戒的兵器。', 1, '示例数据'
  UNION ALL SELECT '六年级', 40, '写作', '综合性学习：为「保护环境，从我做起」主题活动写一份倡议书。\n要求：①写明倡议的原因和具体做法；②格式正确；③不少于 300 字。', NULL, '（略）', '倡议书一般包括标题、称呼、正文（原因 + 倡议内容）、结尾、署名和日期。', 2, '示例数据'
  UNION ALL SELECT '六年级', 41, '写作', '习作：以「心愿」为话题写一篇作文。\n要求：①围绕中心选择材料，做到详略得当；②结构完整，首尾呼应；③不少于 450 字。', NULL, '（略）', '谋篇布局要注意开头点题、中间详写、结尾升华，注意段落之间的过渡。', 3, '示例数据'
  -- ===== 一年级 =====
  UNION ALL SELECT '一年级', 1, '选择', '「花」的声母是（　）', '["A. h","B. f","C. g"]', 'A', '「花」读 huā，声母是 h。', 1, '示例数据'
  UNION ALL SELECT '一年级', 2, '填空', '「水」字共有______画。', NULL, '4', '「水」的笔顺：竖钩、横撇、撇、捺，共 4 画。', 2, '示例数据'
  UNION ALL SELECT '一年级', 3, '填空', '填量词：\n一______牛　　一______笔', NULL, '头；支', '动物的量词常用「头、只」；细长物品用「支、根」。', 1, '示例数据'
  UNION ALL SELECT '一年级', 4, '选择', '下列搭配恰当的一项是（　）', '["A. 鲜艳的红旗","B. 鲜艳的歌声","C. 鲜艳的雷声"]', 'A', '「鲜艳」形容颜色，只能修饰「红旗」。', 1, '示例数据'
  UNION ALL SELECT '一年级', 5, '填空', '照样子写句子：\n例：弯弯的月儿小小的船。\n_____的太阳_____的花。', NULL, '（示例）红红的太阳艳艳的花。', '仿写要注意句式相同，用叠词修饰名词。', 2, '示例数据'
  UNION ALL SELECT '一年级', 6, '写作', '看图写话：图上是一片草地，几个小朋友在放风筝。请写一两句话。', NULL, '（略）', '先看清图上有谁、在做什么，再写一句完整通顺的话。', 1, '示例数据'
) t
WHERE NOT EXISTS (SELECT 1 FROM question q WHERE q.stem = t.s);

-- ------------------------------------------------------------
-- 9. 试卷（3 份）+ 题目编排（paper_question）
--    按标题判重；编排取该卷所需知识点下的一道题（每题只做演示用引用）
-- ------------------------------------------------------------
-- 卷1：五年级 说明方法 专项
INSERT INTO paper (title, grade, paper_type, student_id, status, remark)
SELECT '五年级 说明方法 专项', '五年级', 'KP', NULL, 'READY', '示例数据'
WHERE NOT EXISTS (SELECT 1 FROM paper WHERE title='五年级 说明方法 专项');

-- 卷2：三年级 修辞手法 专项
INSERT INTO paper (title, grade, paper_type, student_id, status, remark)
SELECT '三年级 修辞手法 专项', '三年级', 'KP', NULL, 'READY', '示例数据'
WHERE NOT EXISTS (SELECT 1 FROM paper WHERE title='三年级 修辞手法 专项');

-- 卷3：六年级 文言与名句 专项
INSERT INTO paper (title, grade, paper_type, student_id, status, remark)
SELECT '六年级 文言与名句 专项', '六年级', 'KP', NULL, 'DRAFT', '示例数据'
WHERE NOT EXISTS (SELECT 1 FROM paper WHERE title='六年级 文言与名句 专项');

-- 卷1 编排：五年级 28/31/32 号知识点下的题，按题型顺序
INSERT INTO paper_question (paper_id, question_id, sort_order)
SELECT p.id, q.id, ROW_NUMBER() OVER (ORDER BY FIELD(q.qtype,'选择','填空','判断','阅读','古诗文','写作','其他'), q.id)
FROM paper p
JOIN question q ON q.grade='五年级' AND q.kp_id IN (28,31,32) AND q.source='示例数据'
WHERE p.title='五年级 说明方法 专项'
  AND NOT EXISTS (SELECT 1 FROM paper_question pq WHERE pq.paper_id=p.id AND pq.question_id=q.id);

-- 卷2 编排：三年级 16/17/13 号知识点
INSERT INTO paper_question (paper_id, question_id, sort_order)
SELECT p.id, q.id, ROW_NUMBER() OVER (ORDER BY FIELD(q.qtype,'选择','填空','判断','阅读','古诗文','写作','其他'), q.id)
FROM paper p
JOIN question q ON q.grade='三年级' AND q.kp_id IN (16,17,13) AND q.source='示例数据'
WHERE p.title='三年级 修辞手法 专项'
  AND NOT EXISTS (SELECT 1 FROM paper_question pq WHERE pq.paper_id=p.id AND pq.question_id=q.id);

-- 卷3 编排：六年级 37/38/39 号知识点
INSERT INTO paper_question (paper_id, question_id, sort_order)
SELECT p.id, q.id, ROW_NUMBER() OVER (ORDER BY FIELD(q.qtype,'选择','填空','判断','阅读','古诗文','写作','其他'), q.id)
FROM paper p
JOIN question q ON q.grade='六年级' AND q.kp_id IN (37,38,39) AND q.source='示例数据'
WHERE p.title='六年级 文言与名句 专项'
  AND NOT EXISTS (SELECT 1 FROM paper_question pq WHERE pq.paper_id=p.id AND pq.question_id=q.id);

-- ------------------------------------------------------------
-- 10. 自检（跑完打印各表条数）
-- ------------------------------------------------------------
SELECT 'student' AS 表, COUNT(*) AS 条数 FROM student
UNION ALL SELECT 'course', COUNT(*) FROM course
UNION ALL SELECT 'time_slot', COUNT(*) FROM time_slot
UNION ALL SELECT 'lesson', COUNT(*) FROM lesson
UNION ALL SELECT 'question', COUNT(*) FROM question
UNION ALL SELECT 'paper', COUNT(*) FROM paper
UNION ALL SELECT 'paper_question', COUNT(*) FROM paper_question
UNION ALL SELECT 'lesson_knowledge', COUNT(*) FROM lesson_knowledge;
