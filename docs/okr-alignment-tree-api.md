# OKR 对齐树接口 · 后端实现说明

> 供 Web 页 `docs/okr-alignment-tree.html` 使用。  
> 前端接收 **扁平 objectives 列表**，自行按 `parentKrId` 拼 **多条对齐链**（森林）。

---

## 1. 接口定义

```http
GET /mobile/okr/alignment-tree?periodType=quarter-3&deptId=2
X-User-Id: {当前用户，建议管理员或有 OKR 查看权限的角色}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| periodType | String | 是 | `quarter-1` ~ `quarter-4` / `year`，与 `my-goal` 一致 |
| deptId | Long | 否 | 部门筛选；不传则返回组织内全部链 |

### 响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "periodType": "quarter-3",
    "periodLabel": "Q3 2026",
    "departments": [
      { "id": 1, "name": "公司管理层" },
      { "id": 2, "name": "研发部" }
    ],
    "objectives": [
      {
        "id": 1,
        "title": "2026 公司级战略目标",
        "userId": 10001,
        "ownerName": "张总",
        "deptId": 1,
        "deptName": "公司管理层",
        "periodType": "quarter",
        "startDate": "2026-07-01",
        "endDate": "2026-09-30",
        "status": 0,
        "statusLabel": "进行中",
        "progress": 38,
        "progressText": "1/2 KR (38%)",
        "parentKrId": null,
        "parentKr": null,
        "keyResults": [
          {
            "id": 10,
            "objectiveId": 1,
            "title": "营收同比增长 20%",
            "targetValue": 20,
            "currentValue": 8,
            "unit": "%",
            "status": 0,
            "achieved": false
          },
          {
            "id": 11,
            "objectiveId": 1,
            "title": "客户满意度 ≥ 95%",
            "targetValue": 95,
            "currentValue": 88,
            "unit": "%",
            "status": 0,
            "achieved": false
          }
        ]
      },
      {
        "id": 3,
        "title": "华东区 Q3 业务目标",
        "userId": 10005,
        "ownerName": "李经理",
        "deptId": 3,
        "deptName": "华东区",
        "progress": 55,
        "statusLabel": "进行中",
        "parentKrId": 10,
        "parentKr": {
          "id": 10,
          "title": "营收同比增长 20%",
          "objectiveId": 1,
          "objective": {
            "id": 1,
            "title": "2026 公司级战略目标",
            "userId": 10001
          }
        },
        "keyResults": [
          {
            "id": 20,
            "title": "新签约客户 50 家",
            "targetValue": 50,
            "currentValue": 30,
            "unit": "家"
          }
        ]
      },
      {
        "id": 4,
        "title": "研发效能与交付目标",
        "userId": 10006,
        "ownerName": "王主管",
        "deptId": 2,
        "deptName": "研发部",
        "progress": 43,
        "parentKrId": 10,
        "parentKr": {
          "id": 10,
          "title": "营收同比增长 20%",
          "objectiveId": 1,
          "objective": { "id": 1, "title": "2026 公司级战略目标" }
        },
        "keyResults": []
      }
    ],
    "stats": {
      "objectiveCount": 3,
      "krCount": 3,
      "rootChainCount": 1,
      "orphanObjectiveCount": 0
    }
  }
}
```

### 字段说明

| 字段 | 说明 |
|------|------|
| objectives | **扁平数组**，与 `my-goal.objectives[]` 结构一致 |
| objectives[].ownerName | **新增**：目标负责人展示名（nickName 优先） |
| objectives[].deptName | **新增**：部门名称 |
| objectives[].parentKrId | 对齐的上级 KR；`null` 表示链顶 |
| objectives[].parentKr | 与现网一致，便于前端展示对齐文案 |
| objectives[].keyResults | 该 O 下全部 KR |
| stats.rootChainCount | 链顶 O 的数量（`parentKrId` 为空或上级 KR 不在本周期数据集内） |
| stats.orphanObjectiveCount | 对齐的上级 KR 不在返回集内的 O 数量（可标黄） |

> **不要**在后端拼 ECharts 树；返回扁平列表即可，前端负责多链拼树。

---

## 2. 多条对齐链怎么理解

对齐关系是 **有向边**：`下级 O.parentKrId → 上级 KR.id`

```
链 1:
  公司 O ── KR「营收+20%」 ──┬── 华东区 O ── KR「签约50家」 ── 个人 O
                            └── 研发部 O ── KR「OKR上线」 ── 个人 O

链 2（独立链顶）:
  公司 O ── KR「满意度95%」 ── （暂无下级对齐）
```

特点：

1. **一个 KR 可挂多个下级 O**（同一 KR 分出多条支链）  
2. **一个周期可有多条链顶 O**（多个 `parentKrId = null` 的根）  
3. **链可断开**（下级 O 的 `parentKrId` 指向的 KR 不在本周期/本部门结果集）→ 前端标为「孤立链」仍展示  

---

## 3. 后端查询建议（Java / SQL 思路）

### 3.1 主查询：周期内全部 O + KR

```sql
-- 1) 按 periodType 对应的日期区间筛目标（与 my-goal 逻辑一致）
SELECT o.*, u.nick_name AS owner_name, d.dept_name
FROM okr_objective o
LEFT JOIN sys_user u ON o.user_id = u.user_id
LEFT JOIN sys_dept d ON o.dept_id = d.dept_id
WHERE o.del_flag = 0
  AND o.start_date <= :periodEnd
  AND o.end_date >= :periodStart
-- 可选 AND o.dept_id = :deptId  （见 3.2 闭包）
```

```sql
-- 2) 批量查 KR
SELECT * FROM okr_key_result
WHERE objective_id IN (:objectiveIds) AND del_flag = 0
ORDER BY sort_order
```

```sql
-- 3) 填充 parentKr（与 my-goal 相同 JOIN）
SELECT kr.id, kr.title, kr.objective_id, po.title AS parent_objective_title
FROM okr_key_result kr
JOIN okr_objective po ON kr.objective_id = po.id
WHERE kr.id IN (:parentKrIds)
```

### 3.2 部门筛选（保留完整链）

仅 `dept_id = ?` 会 **截断链条**。建议：

1. 先查出该部门所有 O 的 id 集合 `S0`  
2. **向上闭包**：沿 `parentKrId → KR → objective` 递归，把祖先 O 加入集合  
3. **向下闭包**：找出 `parentKrId` 属于 `S` 中任意 KR 的下级 O，递归加入  
4. 最终用 id 集合 `S` 重新查 O + KR  

伪代码：

```java
Set<Long> ids = objectivesInDept(deptId, period);
boolean changed;
do {
  changed = false;
  // upward: add parent objectives of parentKr
  for (O o : loadByIds(ids)) {
    if (o.getParentKrId() != null) {
      Long parentObjId = krToObjectiveId(o.getParentKrId());
      if (parentObjId != null && ids.add(parentObjId)) changed = true;
    }
  }
  // downward: add objectives aligned to any KR under ids
  Set<Long> krIds = krIdsUnderObjectives(ids);
  for (Long alignedObjId : objectivesWithParentKrIn(krIds)) {
    if (ids.add(alignedObjId)) changed = true;
  }
} while (changed);
return loadFullObjectives(ids);
```

### 3.3 stats 计算

```java
Set<Long> allKrIds = /* 返回集内全部 KR id */;
int roots = 0, orphans = 0;
for (Objective o : objectives) {
  if (o.getParentKrId() == null) roots++;
  else if (!allKrIds.contains(o.getParentKrId())) orphans++;
}
stats.rootChainCount = roots;
stats.orphanObjectiveCount = orphans;
```

### 3.4 权限

- 普通员工：可只看本部门 + 上级链（与 `align-objectives` 范围类似）  
- 管理员 / HR / 目标管理员：看全部  
- 与现有 `X-User-Id` 鉴权体系一致  

---

## 4. 与现有接口的关系

| 现有接口 | 关系 |
|----------|------|
| `GET my-goal` | 单用户版；`objectives[]` 字段与本接口元素相同 |
| `GET align-objectives` | 仅「可对齐」子集；**不能**替代本接口 |
| `GET align-options` | Web 页用于填充部门下拉；可合并进本接口 `departments` |

建议在 `OkrController` 新增：

```java
@GetMapping("/alignment-tree")
public BaseResp<OkrAlignmentTreeResponse> alignmentTree(
    @RequestParam String periodType,
    @RequestParam(required = false) Long deptId,
    @RequestHeader("X-User-Id") Long userId
)
```

---

## 5. 前端拼树规则（已实现于 okr-alignment-tree.html）

```
1. 注册 nodeIndex：obj-{id}、kr-{id}
2. 找出链顶 O：parentKrId 为空，或 parentKrId 不在当前 KR 集合
3. 对每个链顶 O，递归：
     O → 其 keyResults(KR) → 挂在该 KR 下的对齐 O（可能多个）→ 继续递归
4. 多条链顶 → 虚拟根节点「{periodLabel} 组织 OKR」
5. parentKrId 指向缺失 KR 的 O → 仍作为链顶，详情标「上级 KR 未在本周期内」
```

---

## 6. 联调

```bash
curl -s 'http://47.110.156.186:9220/mobile/okr/alignment-tree?periodType=quarter-3' \
  -H 'X-User-Id: 10001' | jq .
```

Web 页 URL 参数（可选）：

```text
okr-alignment-tree.html?api=http://47.110.156.186:9220&userId=10001
```

---

## 7. 检查清单

- [ ] 同一 KR 下挂 2 个下级 O，前端树出现 2 条分支  
- [ ] 2 个无 parentKrId 的公司 O，前端出现 2 条独立链（虚拟根下 2 子树）  
- [ ] 部门筛选后，链顶到选中部门的路径完整、不断裂  
- [ ] `ownerName` / `deptName` 有值  
- [ ] 与 `my-goal` 同一 O 的 progress、keyResults 一致  

**文档版本**：2026-07-09
