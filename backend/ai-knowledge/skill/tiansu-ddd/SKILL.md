---
name: tiansu-ddd
version: 2.0.0
updated: 2026-07-31
description: >
  天溯 energy 系 DDD 轻量落地规范。基于 energy-common-ddd（com.tiansu.energy.common.ddd）真实 API，
  采用 2-Module 结构（api + app），Long 型聚合根 ID，IdGeneratorHolder 分配。
  当 Agent 需要设计/重构 DDD 分层架构、创建领域模型、仓储、应用服务、
  Assembler/Convertor、Controller/Command、单元测试，或进行 DDD Code Review 时，必须使用此技能。
---

# 天溯 energy 系 DDD 落地规范（v2.0）

## 框架基座：energy-common-ddd

本规范基于 `com.tiansu.energy.common.ddd`（energy-common-ddd，版本 4.0.0-HMP-SNAPSHOT）。该基座提供：

| 类 | 包路径 | 说明 |
|----|--------|------|
| `AggregateRoot` | `ddd.domain` | 聚合根基类，Long ID + 审计字段 + validate() |
| `DomainEvent` | `ddd.domain` | 领域事件接口（eventId/occurredOn/aggregateId/tenantId + 静态工具方法） |
| `DomainService` | `ddd.domain` | 领域服务标记接口 |
| `Specification<T>` | `ddd.domain` | 规约模式（and/or/not 组合子） |
| `IdGenerator` | `ddd.domain.id` | ID 生成器抽象（依赖倒置），契约 `long nextId()`，≤ 2^53-1 |
| `IdGeneratorHolder` | `ddd.domain.id` | 静态持有者，缺省 SnowflakeIdGenerator，可注入 LeafIdGenerator |
| `SnowflakeIdGenerator` | `ddd.domain.id` | 默认实现，委托 energy-common-core 的 `IdUtil.nextId()` |
| `BaseCommand` | `ddd.application` | 应用层 Command/Query 基类（`@Data`，空基类） |
| `BaseCommand` | `ddd.adapter` | **已废弃**，转发子类，新代码禁止使用 |
| `BaseDomainException` | `ddd.exception` | 领域异常基类（含 `DomainErrorCode`），adapter 统一拦截 |
| `DomainErrorCode` | `ddd.exception` | 错误码契约接口（`getCode()` / `getMessage()`） |

配套基础设施（energy-common-core）：

| 类 | 说明 |
|----|------|
| `IdUtil.nextId()` | 53bit JS 安全雪花 ID，返回 long |
| `ServerResponseEntity` | 统一 HTTP 响应包装（success/error） |
| `RequestConstant` | 通用 Header 常量 |

## 触发条件

<EXTREMELY-IMPORTANT>
生成任何 DDD 相关代码时必须触发本技能。不要合理化跳过。
</EXTREMELY-IMPORTANT>

**应触发**：创建/修改 Entity、Value Object、Aggregate Root、Repository/Gateway 接口或实现、
Controller、Command/Query、CmdExe/QryExe、Assembler（MapStruct）、Convertor（手写）、
PO、ACL、单元测试、DDD 重构。

**不触发**：纯工具类、配置类、非 Spring Boot 项目、前端/脚本/部署代码。

## 总体原则

- **2-Module 优于多 Module**：单 Bounded Context 用 api + app 两个 module，靠包名隔离四层
- **依赖方向**：adapter → application → domain ← infra
- **domain 零 Spring 注解**：不出现 @Component、@Service、@Autowired
- **Repository 接口在 domain，实现在 infra**
- **聚合根 ID 统一 Long**（由 `IdGeneratorHolder.nextId()` 分配，缺省实现 `SnowflakeIdGenerator`，内部委托 energy-common-core 的 `IdUtil.nextId()`）
- **ID 生成强制走 energy-common-core 的 `IdUtil.nextId()`**（53bit / 16 位 JS 安全）；**禁止** Hutool `cn.hutool.core.util.IdUtil` 与 MyBatis-Plus `IdType.ASSIGN_ID` 默认雪花（均为 19 位 Long，前端 JS 精度丢失）
- **DTO/RequestDTO 只在 api module**，不污染内部实现
- **Command/Query 是应用层用例入参**：ServiceImpl 入口将 RequestDTO 归一为 Command/Query
- **用例命名（分层不同名，禁止混用）**：api 契约层分页/查询入参 `{Xxx}PageQueryDTO`（带 DTO 后缀）；application 层查询对象 `{Xxx}PageQuery`、命令对象 `{Xxx}CreateCommand`；执行器 `{Xxx}CreateCmdExe` / `{Xxx}PageQryExe`——**查询执行器只能用 `QryExe`，禁止 `QueryExe`**
- **事务边界在 Application 层**：@Transactional 在 CmdExe 上，RepositoryImpl 不开事务
- **转换器选型**：纯字段搬运用 MapStruct（`unmappedTargetPolicy = ERROR`）；含构造语义的手写

## 模块结构

```
{project}/
  pom.xml
  {project}-api/
    src/main/java/com/tiansu/mall/{project}/client/{context}/
      {Xxx}CreateRequestDTO.java   # jakarta.validation 校验注解
      {Xxx}UpdateRequestDTO.java
      {Xxx}ResponseDTO.java
      api/{Xxx}Service.java        # 对外契约接口，返回 ServerResponseEntity<T>
  {project}-app/
    src/main/java/com/tiansu/mall/{project}/
      adapter/
        web/{Xxx}Controller.java          # @Valid 绑定 RequestDTO，薄层委派
        app/dto/                          # 仅端协议与契约不一致时才建
        enhance/                          # 可选：命令增强/拦截
      application/{context}/
        command/{Xxx}CreateCommand.java   # 继承 BaseCommand，@Data + @EqualsAndHashCode
        query/{Xxx}PageQuery.java
        executor/command/{Xxx}CreateCmdExe.java
        service/{Xxx}ServiceImpl.java     # RequestDTO 止步于此
        assembler/{Xxx}Assembler.java     # MapStruct
      domain/{context}/
        model/{Xxx}.java                  # 聚合根，继承 AggregateRoot
        gateway/{Xxx}Repository.java
        domainservice/{Xxx}DomainService.java + impl/
        errorcode/{Xxx}ErrorCode.java + {Xxx}DomainException.java
        event/{Xxx}Event.java             # 领域事件（Java record）
      infra/
        gateway/impl/
          mapper/{Xxx}Mapper.java         # MyBatis-Plus Mapper 接口：extends BaseMapper<{Xxx}DB>
          database/{Xxx}DB.java           # 表实体 PO/DO：@TableName 映射表结构，继承 BaseEntity
        acl/{GateImpl.java,feign/}
        convertor/{Base,Xxx}Convertor.java  # 手写
        config/
```

**依赖关系**：`{project}-app → {project}-api`（api 无内部依赖，纯 POJO）

---

## 逐层规范

### 1. API 层（api module）

**职责**：定义对外契约。仅含 RequestDTO / PageQueryDTO / ResponseDTO、Service 接口。

**规则**：
- **入参命名（api 契约层统一带 DTO 后缀）**：写操作入参 `{Xxx}CreateRequestDTO` / `{Xxx}UpdateRequestDTO`；分页/查询入参 `{Xxx}PageQueryDTO`（继承 `PageParamDTO`）。**api 层禁止出现无 DTO 后缀的 `{Xxx}Query`**
- 允许 Lombok `@Data` + `jakarta.validation` 校验注解，禁止 Spring/Swagger 注解
- 格式校验在 RequestDTO / PageQueryDTO，业务不变量校验在聚合根 `validate()`
- Service 接口返回 `ServerResponseEntity<T>`

```java
// --- api module ---
@Data
public class QuestionCreateRequestDTO {
    @NotBlank(message = "题干不能为空")
    private String name;
    @NotBlank(message = "题型不能为空")
    private String type;     // SINGLE_CHOICE / MULTIPLE_CHOICE / JUDGMENT / QA
    @NotBlank(message = "所属分组不能为空")
    private String groupId;
    private String answer;
    private String analysis;
    @Valid
    private List<QuestionOptionDTO> options;
}

public interface QuestionService {
    ServerResponseEntity<Long> createQuestion(QuestionCreateRequestDTO request);
}
```

### 2. Adapter 层

**职责**：接收 HTTP 请求，`@Valid` 绑定 RequestDTO，薄层委派给 api Service。

**规则**：
- Controller 直接绑定 api RequestDTO 作为请求体
- 禁止 Swagger/OpenAPI 注解
- 多通道按 `adapter/{context}/{channel}/` 分包（web/app/feign）
- 端协议与契约一致时不建端 DTO；不一致时建端 DTO + MapStruct 适配

```java
// --- adapter/web ---
@RestController
public class QuestionController {
    @Resource
    private QuestionService questionService;

    @PostMapping("/api/v1/questions")
    public ServerResponseEntity<Long> createQuestion(
            @RequestBody @Valid QuestionCreateRequestDTO request) {
        return questionService.createQuestion(request);
    }
}
```

### 3. Application 层

**职责**：任务编排、事务协调、RequestDTO → Command 归一。

**规则**：
- Command 继承 `ddd.application.BaseCommand`（`@Data`），子类加 `@EqualsAndHashCode(callSuper = true)`
- ServiceImpl 是 RequestDTO 终点：归一为 Command 后分发给 CmdExe
- **RequestDTO 禁止向内渗透**到 CmdExe/QryExe/Domain（红线 #8）
- @Transactional 加在 CmdExe 方法上
- 远程调用（UserGateway）在事务外执行
- Assembler 用 MapStruct（`@BeanMapping(unmappedTargetPolicy = ERROR)`）

```java
// --- application/{context}/command ---
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionCreateCommand extends BaseCommand {
    private String name;
    private String type;
    private String groupId;
    private String answer;
    private String analysis;
    private List<QuestionOptionDTO> options;
}

// --- application/{context}/service ---
@Component
public class QuestionServiceImpl implements QuestionService {
    @Resource
    private QuestionCreateCmdExe questionCreateCmdExe;
    @Resource
    private QuestionAssembler questionAssembler;
    @Resource
    private UserGateway userGateway;

    @Override
    public ServerResponseEntity<Long> createQuestion(QuestionCreateRequestDTO request) {
        // 1. RequestDTO → Command（RequestDTO 止步于此）
        QuestionCreateCommand command = questionAssembler.toCreateCommand(request);
        // 2. 事务外解析用户（避免远程调用占 DB 连接）
        LoginUser loginUser = userGateway.getUser();
        // 3. 执行用例
        Long id = questionCreateCmdExe.execute(command, loginUser);
        return ServerResponseEntity.success(id);
    }
}

// --- application/{context}/assembler ---
@Mapper(componentModel = "spring")
public interface QuestionAssembler {
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    QuestionCreateCommand toCreateCommand(QuestionCreateRequestDTO request);
    List<QuestionItem> toQuestionItems(List<QuestionOptionDTO> options);
}

// --- application/{context}/executor/command ---
@Component
public class QuestionCreateCmdExe {
    @Resource
    private QuestionRepository questionRepository;
    @Resource
    private QuestionAssembler questionAssembler;

    @Transactional(rollbackFor = Exception.class)
    public Long execute(QuestionCreateCommand command, LoginUser loginUser) {
        QuestionType questionType = QuestionType.byCode(command.getType());
        List<QuestionItem> items = questionAssembler.toQuestionItems(command.getOptions());
        // 静态工厂创建聚合（自动分配 ID + 自校验）
        Question question = Question.create(
            command.getGroupId(), command.getName(), questionType,
            items, command.getAnswer(), command.getAnalysis());
        // 回填审计
        question.initCreateInfo(loginUser.getId(), loginUser.getUsername());
        // 持久化
        questionRepository.save(question);
        return question.getId();
    }
}
```

### 4. Domain 层

**职责**：核心业务逻辑。零框架依赖，可脱离 Spring 独立测试。

**关键 API（来自 energy-common-ddd）**：

```
AggregateRoot
├── protected AggregateRoot()           // 新建：自动分配 Long ID（IdGeneratorHolder.nextId()）
├── protected AggregateRoot(Long id)    // 恢复：保留已有 ID，不重新分配
├── public void validate()              // 子类重写做跨字段校验
├── public void initCreateInfo(String, String)
└── public void initUpdateInfo(String, String)
```

**聚合根设计规则**：
- 继承 `AggregateRoot`，使用 `@Getter`（禁止 `@Data`，不暴露 setter）
- **新建场景**：子类构造器调 `super()` → 自动分配 ID → 调用 `this.validate()`
- **恢复场景**：子类构造器调 `super(id)` → 保留已有 ID → **不调 validate()**（脏数据不进校验）
- 校验失败抛 `*DomainException`（继承 `BaseDomainException`，含 `DomainErrorCode`）

```java
// --- domain/{context}/model ---
@Getter
public class Question extends AggregateRoot {
    private String groupId;
    private String name;
    private QuestionType questionType;
    private List<QuestionItem> questionItems;
    private String answer;
    private String analysis;

    // 【新建】构造器：super() 自动分配 ID
    private Question(String groupId, String name, QuestionType questionType,
                     List<QuestionItem> questionItems, String answer, String analysis) {
        super();    // IdGeneratorHolder.nextId() 分配 Long ID
        this.groupId = groupId;
        this.name = name;
        this.questionType = questionType;
        this.questionItems = questionItems;
        this.answer = answer;
        this.analysis = analysis;
        this.validate();
    }

    // 【恢复】构造器：super(id) 保留已有 ID，不调 validate()
    private Question(Long id, String groupId, String name, QuestionType questionType,
                     List<QuestionItem> questionItems, String answer, String analysis) {
        super(id);  // 保留持久层的 ID
        this.groupId = groupId;
        this.name = name;
        this.questionType = questionType;
        this.questionItems = questionItems;
        this.answer = answer;
        this.analysis = analysis;
        // 注意：不调 validate()！历史数据可能不满足当前校验规则
    }

    // 静态工厂：新建聚合
    public static Question create(String groupId, String name, QuestionType questionType,
                                  List<QuestionItem> questionItems, String answer, String analysis) {
        return new Question(groupId, name, questionType, questionItems, answer, analysis);
    }

    // 静态工厂：从持久层恢复聚合
    public static Question restore(Long id, String groupId, String name, QuestionType questionType,
                                   List<QuestionItem> questionItems, String answer, String analysis) {
        return new Question(id, groupId, name, questionType, questionItems, answer, analysis);
    }

    @Override
    public void validate() {
        if (questionType == QuestionType.SINGLE_CHOICE
            && questionItems.stream().filter(QuestionItem::isCorrect).count() != 1) {
            throw new QuestionDomainException(QuestionErrorCode.SINGLE_CHOICE_ANSWER_INVALID);
        }
        if (questionType == QuestionType.MULTIPLE_CHOICE
            && questionItems.stream().filter(QuestionItem::isCorrect).count() < 2) {
            throw new QuestionDomainException(QuestionErrorCode.MULTIPLE_CHOICE_ANSWER_INVALID);
        }
    }
}

// --- domain/{context}/errorcode ---
public enum QuestionErrorCode implements DomainErrorCode {
    NAME_EMPTY("题干不能为空"),
    TYPE_INVALID("题型无效"),
    SINGLE_CHOICE_ANSWER_INVALID("单选题答案只能有一个"),
    MULTIPLE_CHOICE_ANSWER_INVALID("多选题答案至少有两个");

    private final String message;
    QuestionErrorCode(String message) { this.message = message; }
    @Override public String getCode() { return name(); }
    @Override public String getMessage() { return message; }
}

public class QuestionDomainException extends BaseDomainException {
    public QuestionDomainException(QuestionErrorCode errorCode) { super(errorCode); }
    public QuestionDomainException(QuestionErrorCode errorCode, String detail) { super(errorCode, detail); }
}
```

**Value Object**：

```java
@Getter
@AllArgsConstructor
public class QuestionItem {
    private Integer seq;
    private String content;
    private Integer correctFlag;   // 1=正确, 0=错误
    public Boolean isCorrect() { return Objects.equals(1, correctFlag); }
}
```

**Repository / Gateway 接口（定义在 domain）**：

```java
public interface QuestionRepository {
    Long save(Question question);       // 返回 Long ID
    Question byId(Long id);
    void remove(Long id);
}
```

**领域事件（DomainEvent）**：用 Java record 实现 `DomainEvent` 接口：

```java
public record QuestionCreatedEvent(
    String eventId,
    Instant occurredOn,
    String aggregateId,    // Long → String.valueOf(question.getId())
    String tenantId,
    Long questionId,
    String questionType
) implements DomainEvent {
    public static QuestionCreatedEvent of(Question question) {
        return new QuestionCreatedEvent(
            DomainEvent.newEventId(),
            DomainEvent.now(),
            String.valueOf(question.getId()),
            question.getTenantId(),  // 由聚合提供
            question.getId(),
            question.getQuestionType().getCode()
        );
    }
}
```

**全局异常拦截**（adapter 层，统一按 `BaseDomainException` 拦截）：

```java
@Order(0)
@RestControllerAdvice
public class DomainExceptionHandler {
    @ExceptionHandler(BaseDomainException.class)
    public ServerResponseEntity<Void> handle(BaseDomainException e) {
        return ServerResponseEntity.error(e.getMessage());
    }
}
```

**Domain Service 判断逻辑**：
```
跨聚合协作？ → 是 → DomainService
           → 否 → 逻辑属于聚合本身？ → 是 → 聚合方法
                                    → 否 → 需要独立扩展点？ → 是 → DomainService
                                                            → 否 → 静态工厂
```

### 5. Infrastructure 层

**职责**：技术实现（DB、RPC、MQ），实现 domain 层定义的接口。

**Convertor 手写规则**：
- 含 restore 工厂（调 `Question.restore(id, ...)` 用已有 ID）
- 枚举 ↔ code 转码
- PO 字段可能裁剪/合并，非纯 JavaBean 拷贝

```java
@TableName("tem_question_library")
public class QuestionLibraryDB {
    @TableId(type = IdType.INPUT)   // 主键由 IdUtil.nextId() 赋值（聚合根 super() 已生成），禁走默认 ASSIGN_ID
    private Long id;          // Long 型主键
    private String name;
    private String type;
    private String groupId;
    private String answer;
    private String analysis;
    // getter/setter...
}

public class QuestionLibraryConvertor {

    /** Question → QuestionLibraryDB */
    public static QuestionLibraryDB toDB(Question question) {
        QuestionLibraryDB po = new QuestionLibraryDB();
        po.setId(question.getId());
        po.setType(question.getQuestionType().getCode());
        po.setName(question.getName());
        po.setGroupId(question.getGroupId());
        po.setAnswer(question.getAnswer());
        po.setAnalysis(question.getAnalysis());
        return po;
    }

    /** DB → Question（恢复，使用已有 ID，不校验） */
    public static Question toDomain(QuestionLibraryDB po, List<QuestionOptionDB> options) {
        return Question.restore(           // 调 restore 工厂，非 create
            po.getId(),                    // 保留已有 Long ID
            po.getGroupId(), po.getName(),
            QuestionType.byCode(po.getType()),
            toQuestionItems(options),
            po.getAnswer(), po.getAnalysis()
        );
    }
    // toQuestionItems(...) 略
}
```

## 请求处理链路（创建试题）

```
HTTP POST /api/v1/questions
    │
    ▼
QuestionController.createQuestion(@Valid QuestionCreateRequestDTO)
    │  （@Valid 触发格式校验，失败 → 400）
    ▼
QuestionServiceImpl.createQuestion(request)
    │  questionAssembler.toCreateCommand(request)   // RequestDTO → Command，止步于此
    │  userGateway.getUser()                        // 事务外解析用户
    ▼  ── @Transactional ──
QuestionCreateCmdExe.execute(command, loginUser)
    │  Question.create(...)           // super() 分配 ID + validate()
    │  question.initCreateInfo(...)   // 审计回填
    │  questionRepository.save(q)
    ▼
QuestionRepositoryImpl.save(question)
    │  Convertor.toDB(question)       // Domain → PO（ID 来自聚合）
    │  mapperService.saveOrUpdate(po)
    │  先删后插 options               // 同一事务内
    ▼
返回 question.getId()  →  ServerResponseEntity.success(id)  →  HTTP 200
```

---

## 红线规则

### 刚性（违反 = 不合格）

| # | 规则 |
|---|------|
| 1 | domain 层禁止 Spring 注解 |
| 2 | domain 层抛 `*DomainException`（继承 `BaseDomainException`，含 `DomainErrorCode`），禁止抛 ApiException |
| 3 | Entity 继承 `AggregateRoot`，禁止继承 DbBaseEntity |
| 4 | Repository 接口在 domain，实现在 infra |
| 5 | 新建聚合调 `super()`（自动分配 Long ID）+ `this.validate()`；**恢复聚合调 `super(id)` + 不调 validate()** |
| 6 | 审计字段通过 `initCreateInfo()` / `initUpdateInfo()` 回填 |
| 7 | RequestDTO 只在 api module，禁止出现在 app module |
| 8 | CmdExe/QryExe/Domain 禁止出现 RequestDTO；ServiceImpl 入口归一为 Command |
| 9 | 纯字段搬运用 MapStruct（`unmappedTargetPolicy = ERROR`）；Domain→PO 手写 |
| 10 | @Transactional 在 CmdExe，RepositoryImpl 不开事务 |
| 11 | 禁止 Swagger/OpenAPI 注解 |

### 柔性（建议遵守）

| # | 规则 |
|---|------|
| 1 | URL RESTful，避免动词（/save、/add） |
| 2 | 单 BC 不超过 3 个 module |
| 3 | CmdExe 中先回填审计再 save |
| 4 | 避免 DomainService 过薄（仅包装 new） |
| 5 | 禁止在有 DDD 基座包时重复自建基础类 |

---

## 日志规范

> 完整通用规范见 `java-logging-standard` skill。以下为 DDD 分层架构下的日志策略。

### 分层日志策略

| 层 | 日志策略 |
|----|---------|
| **Controller** | 原则上不打。框架统一拦截（access log + 全局异常处理），业务日志下沉到 Application 层 |
| **Application (CmdExe/QryExe)** | **核心埋点层**。用例入口打 event + 关键 ID，出口打结果，异常打完整上下文 |
| **Domain** | 通常不打。业务违规通过 `*DomainException` 表达（异常即日志）。跨聚合 DomainService 可按需打 WARN |
| **Infra (Repo/ACL)** | **外部调用埋点**。慢查询（>100ms）、Feign 超时/降级、MQ 发送失败打 WARN/ERROR |

### Domain 层可以用 SLF4J

`@Slf4j` 是 Lombok 注解，不是 Spring 注解，**不违反红线 #1**。跨聚合协调的 `DomainServiceImpl` 可以打日志，但同一聚合内的校验/计算仍用异常表达。

### CmdExe 日志模板

```java
@Component
public class QuestionCreateCmdExe {
    private static final Logger log = LoggerFactory.getLogger(QuestionCreateCmdExe.class);

    @Transactional(rollbackFor = Exception.class)
    public Long execute(QuestionCreateCommand command, LoginUser loginUser) {
        log.info("event=question_create_start orderId={} userId={} type={}",
                 command.getGroupId(), loginUser.getId(), command.getType());

        try {
            Question question = Question.create(...);
            question.initCreateInfo(loginUser.getId(), loginUser.getUsername());
            questionRepository.save(question);

            log.info("event=question_create_success questionId={} userId={} costMs={}",
                     question.getId(), loginUser.getId(), costMs);
            return question.getId();

        } catch (QuestionDomainException e) {
            log.warn("event=question_create_rejected questionType={} errorCode={} userId={}",
                     command.getType(), e.getErrorCode().getCode(), loginUser.getId());
            throw e;  // 不重复打堆栈，由全局异常处理器兜底
        } catch (Exception e) {
            log.error("event=question_create_failed userId={} type={}",
                      loginUser.getId(), command.getType(), e);  // 完整堆栈
            throw e;
        }
    }
}
```

### 关键约定

1. **异常日志带 ≥3 个业务 ID**，最后一个参数传 `Throwable e`
2. **不既打又抛**——捕获处打日志就别再往上抛，或者包装后抛但不在捕获处打
3. **不打大对象**（`log.info("order: {}", order)`），只打关键字段
4. **敏感字段脱敏**（密码/token/身份证），详见 `java-logging-standard`
5. **RepositoryImpl 的慢查询**加耗时判断：
   ```java
   long start = System.currentTimeMillis();
   mapperService.saveOrUpdate(po);
   if (System.currentTimeMillis() - start > 100) {
       log.warn("event=slow_db_save table=question costMs={}", System.currentTimeMillis() - start);
   }
   ```

---

## 查询与读模型

- **简单查询**（单对象/列表）：Mapper 直接返回 ResponseDTO，跳过 Domain 层
- **分页查询**：Controller 组装 MyBatis-Plus `Page`，ServiceImpl → QryExe → Mapper，返回 `IPage<ResponseDTO>`
- **复杂查询**（跨聚合/报表）：定义独立 `application/{context}/query/` 包，QryExe 编排

```java
// ServiceImpl 中的分页查询示例
public ServerResponseEntity<IPage<QuestionResponseDTO>> pageQuestions(QuestionPageQuery query) {
    IPage<QuestionLibraryDB> page = questionQryExe.execute(query);
    return ServerResponseEntity.success(page.convert(QuestionAssembler.INSTANCE::toResponse));
}
```

---

## 单元测试规范

### 分层策略

| 层 | 方式 | Spring | 覆盖重点 |
|----|------|:------:|---------|
| Domain | JUnit 5 + AssertJ | 无 | 聚合工厂不变量、状态流转、值对象、枚举 |
| Application | JUnit 5 + Mockito | 无 | 用例编排、异常分支、mock 交互 verify |
| Assembler | `Mappers.getMapper()` | 无 | @Mapping 映射、嵌套集合 |
| Convertor | JUnit 5 | 无 | Domain↔PO 往返、枚举转码 |
| Controller | MockMvc（可选） | 切片 | 路径/参数绑定 |

**关键约定**：
- 单测**不使用 `@SpringBootTest`**
- CmdExe 的 Assembler 用 `@Spy` + `Mappers.getMapper()` 注入真实实现，只 mock 出边界（Repository/Gateway）
- 领域异常断言到**具体 ErrorCode**：
  ```java
  assertThatThrownBy(() -> Question.create(null, ...))
      .isInstanceOfSatisfying(QuestionDomainException.class,
          e -> assertThat(e.getErrorCode()).isEqualTo(QuestionErrorCode.NAME_EMPTY));
  ```
- 恢复工厂测试：确认 ID 保留、脏数据不报错
- 模块 pom 必须显式声明 `maven-surefire-plugin` 3.x（父 pom 未管理时缺省 2.12.4 不识别 JUnit 5，现象是 BUILD SUCCESS 但 Tests run: 0）

### 覆盖基线

| 必测对象 | 最小用例 |
|---------|---------|
| 聚合 create 工厂 | 成功 + 每个必填项缺失 |
| 聚合 restore 工厂 | ID/关联保留 + 脏数据不抛异常 |
| 聚合行为方法 | 正常 + 非法入参 + 幂等 |
| 值对象/枚举 | validate 各分支 + byCode 非法值 |
| CmdExe | 成功编排(verify) + 每个异常分支阻断副作用 |
| Assembler/Convertor | 全字段映射断言 + null/空集合边界 |

---

## 框架能力速查

| 你要做的事 | 用这个 |
|-----------|--------|
| 分配聚合根 ID | `super()`（构造器内自动调用 `IdGeneratorHolder.nextId()`） |
| 恢复聚合根（保留已有 ID） | `super(existingId)` |
| 业务校验 | 重写 `validate()`，抛 `XxxDomainException` |
| 审计回填 | `initCreateInfo(userId, username)` |
| 领域事件 | record 实现 `DomainEvent`，`DomainEvent.newEventId()` / `DomainEvent.now()` |
| 业务规则组合 | `Specification<T>` 的 `and()`/`or()`/`not()` |
| 切换 ID 生成器 | 启动时 `IdGeneratorHolder.init(new LeafIdGenerator())` |
| Command 基类 | 继承 `com.tiansu.energy.common.ddd.application.BaseCommand` |

---

## 常见坑

1. **ID 类型不对**：聚合 ID 是 `Long`，不是 `String`。别用 `IdUtilString`，那是旧 nts-application-starter 的。
2. **ID 生成器用错**：必须用 energy-common-core 的 `com.tiansu.energy.core.util.IdUtil.nextId()`（53bit / 16 位，JS 安全）。禁止 Hutool 的 `cn.hutool.core.util.IdUtil`（`nextId()` / `getSnowflakeNextId()` / `createSnowflake()`）和 MyBatis-Plus `@TableId(type = IdType.ASSIGN_ID)` 默认雪花——两者都是 **19 位 Long**，超出 JS `Number.MAX_SAFE_INTEGER`，前端 `JSON.parse` 会精度丢失。实体主键用 `@TableId(type = IdType.INPUT)`，值由聚合根 `super()` 或显式 `IdUtil.nextId()` 赋值。
3. **恢复聚合忘了保留 ID**：调 `restore()` 工厂而不是 `create()`，否则 DB 里已有的 ID 会被覆盖为新 ID。
4. **恢复聚合调了 validate()**：历史数据可能不满足当前校验规则，恢复时跳过校验。
5. **误用 adapter.BaseCommand**：已 `@Deprecated`，用 `application.BaseCommand`。
6. **maven-surefire-plugin 版本**：父 pom 没管理时缺省 2.12.4，JUnit 5 测试静默跳过。模块 pom 显式声明 3.x。
7. **maven-compiler-plugin skip=true**：宿主根 pom 若全局 skip，新模块 pom 需显式 `<skip>false</skip>`。

---

**互补规范**：tiansu-java-standards（全量 Java 编码规范），本规范专注 DDD 分层架构落地。
