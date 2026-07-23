### T5. ExpenseMapper

**Files:**
- Create: `backend/src/main/resources/mapper/expense/ExpenseMapper.xml`
- Create: `backend/src/main/java/com/lifepulse/expense/repository/ExpenseMapper.java`

**Interfaces:**
- Consumes: T3 (Expense entity), T4 (ExpenseFilter)
- Produces: 6 query methods

- [ ] **Step 1**: 创建接口

```java
package com.lifepulse.expense.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifepulse.expense.dto.ExpenseFilter;
import com.lifepulse.expense.entity.Expense;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ExpenseMapper extends BaseMapper<Expense> {

  Expense findByUserAndId(@Param("userId") Long userId, @Param("id") Long id);

  List<Expense> listByUser(@Param("userId") Long userId,
                            @Param("category") String category,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            @Param("offset") int offset,
                            @Param("size") int size);

  long countByUser(@Param("userId") Long userId,
                   @Param("category") String category,
                   @Param("from") LocalDateTime from,
                   @Param("to") LocalDateTime to);

  Map<String, BigDecimal> summaryByCategory(@Param("userId") Long userId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

  BigDecimal summaryTotal(@Param("userId") Long userId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);
}
```

- [ ] **Step 2**: 创建 XML 映射

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.lifepulse.expense.repository.ExpenseMapper">

  <resultMap id="BaseMap" type="com.lifepulse.expense.entity.Expense">
    <id property="id" column="id"/>
    <result property="userId" column="user_id"/>
    <result property="amount" column="amount"/>
    <result property="category" column="category"
            typeHandler="com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler"/>
    <result property="note" column="note"/>
    <result property="occurredAt" column="occurred_at"/>
    <result property="createdAt" column="created_at"/>
    <result property="updatedAt" column="updated_at"/>
    <result property="deleted" column="deleted"/>
  </resultMap>

  <select id="findByUserAndId" resultMap="BaseMap">
    SELECT * FROM t_expense
    WHERE user_id = #{userId} AND id = #{id} AND deleted = 0
    LIMIT 1
  </select>

  <select id="listByUser" resultMap="BaseMap">
    SELECT * FROM t_expense
    WHERE user_id = #{userId}
      AND deleted = 0
      <if test="category != null">AND category = #{category}</if>
      <if test="from != null">AND occurred_at &gt;= #{from}</if>
      <if test="to != null">AND occurred_at &lt;= #{to}</if>
    ORDER BY occurred_at DESC, id DESC
    LIMIT #{offset}, #{size}
  </select>

  <select id="countByUser" resultType="long">
    SELECT COUNT(*) FROM t_expense
    WHERE user_id = #{userId}
      AND deleted = 0
      <if test="category != null">AND category = #{category}</if>
      <if test="from != null">AND occurred_at &gt;= #{from}</if>
      <if test="to != null">AND occurred_at &lt;= #{to}</if>
  </select>

  <select id="summaryByCategory" resultType="map">
    SELECT category AS k, COALESCE(SUM(amount), 0) AS v
    FROM t_expense
    WHERE user_id = #{userId} AND deleted = 0
      <if test="from != null">AND occurred_at &gt;= #{from}</if>
      <if test="to != null">AND occurred_at &lt;= #{to}</if>
    GROUP BY category
  </select>

  <select id="summaryTotal" resultType="bigdecimal">
    SELECT COALESCE(SUM(amount), 0) FROM t_expense
    WHERE user_id = #{userId} AND deleted = 0
      <if test="from != null">AND occurred_at &gt;= #{from}</if>
      <if test="to != null">AND occurred_at &lt;= #{to}</if>
  </select>

</mapper>
```

- [ ] **Step 3**: 编译 + mapper 注册确认

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS（MyBatis-Plus 默认扫描 `classpath*:/mapper/**/*.xml`；若未扫描，需在 application.yml 显式 `mybatis-plus.mapper-locations: classpath*:/mapper/**/*.xml`）

- [ ] **Step 4**: Commit

```bash
git add backend/src/main/java/com/lifepulse/expense/repository/ExpenseMapper.java \
        backend/src/main/resources/mapper/expense/ExpenseMapper.xml
git commit -m "feat(expense): add ExpenseMapper with 5 custom queries"
```

---
