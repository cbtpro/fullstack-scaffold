package com.scaffold.user.service;

import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import com.scaffold.user.dto.DictQuery;
import com.scaffold.user.entity.DictItem;
import com.scaffold.user.entity.DictType;
import com.scaffold.user.enums.EnableStatus;
import com.scaffold.user.support.ItDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 字典管理业务规则测试（DB 集成）。
 *
 * <p>测的是「界面点下去也不该被破坏」的约束：编码唯一、项归属不可改、
 * 删类型连带删项、停用的类型与项不下发给下拉。这些规则散落在实现里，
 * 不写用例就只能靠人点界面回归。
 *
 * <p>用例建的类型统一登记，跑完删掉，避免共用测试库越跑越脏。
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("databaseReachable")
class DictServiceBusinessTest {

    /** 与历史数据隔离：用例自身的编码都带唯一后缀 */
    private static final AtomicInteger SEQ = new AtomicInteger();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        ItDatabase.register(registry);
    }

    @SuppressWarnings("unused")
    static boolean databaseReachable() {
        return ItDatabase.isReachable();
    }

    @Autowired
    private DictService dictService;

    private final List<Long> createdTypeIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // 用例内可能已自行删除，删除失败即视为清理完成
        for (Long id : createdTypeIds) {
            try {
                dictService.deleteType(id);
            } catch (BizException ignored) {
                // 已不存在
            }
        }
        createdTypeIds.clear();
    }

    private static String unique(String prefix) {
        return prefix + "_" + System.nanoTime() + "_" + SEQ.incrementAndGet();
    }

    /** 建类型并登记，供清理阶段回收（其字典项随类型一并删除） */
    private DictType createType(String code) {
        DictType type = new DictType();
        type.setCode(code);
        type.setName("测试类型 " + code);
        type.setSort(99);
        DictType created = dictService.createType(type);
        createdTypeIds.add(created.getId());
        return created;
    }

    private void createItem(Long typeId, String value) {
        DictItem item = new DictItem();
        item.setTypeId(typeId);
        item.setLabel("文本 " + value);
        item.setValue(value);
        item.setSort(1);
        dictService.createItem(item);
    }

    private DictItem itemOf(Long typeId, String value) {
        DictItem item = new DictItem();
        item.setTypeId(typeId);
        item.setLabel("文本 " + value);
        item.setValue(value);
        item.setSort(1);
        return item;
    }

    @Test
    @DisplayName("新建类型：编码必填且唯一，缺省状态为启用")
    void createTypeValidatesCode() {
        assertThatThrownBy(() -> dictService.createType(new DictType()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ResultCode.PARAM_INVALID.getCode()));

        String code = unique("it_dict_type");
        DictType created = createType(code);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(EnableStatus.ENABLED);

        DictType duplicate = new DictType();
        duplicate.setCode(code);
        duplicate.setName("重复编码");
        assertThatThrownBy(() -> dictService.createType(duplicate))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("停用的类型不下发给下拉：listEnabledTypes 与按编码取项都取不到")
    void disabledTypeIsNotPublished() {
        String code = unique("it_dict_off");
        DictType type = createType(code);
        createItem(type.getId(), "A");

        assertThat(dictService.listEnabledTypes()).extracting(DictType::getId).contains(type.getId());
        assertThat(dictService.listItemsByTypeCode(code)).extracting(DictItem::getValue).contains("A");

        DictType disable = new DictType();
        disable.setId(type.getId());
        disable.setStatus(EnableStatus.DISABLED);
        dictService.updateType(disable);

        assertThat(dictService.listEnabledTypes()).extracting(DictType::getId).doesNotContain(type.getId());
        // 此时按编码取项应返回空，而不是把已停用类型的项照旧下发
        assertThat(dictService.listItemsByTypeCode(code)).isEmpty();
    }

    @Test
    @DisplayName("字典项：同类型下取值唯一、跨类型可重复；停用项不下发")
    void itemValueUniqueWithinType() {
        DictType typeA = createType(unique("it_dict_a"));
        DictType typeB = createType(unique("it_dict_b"));

        DictItem itemA = dictService.createItem(itemOf(typeA.getId(), "SAME"));
        // 同一取值挂到别的类型下是合法的
        createItem(typeB.getId(), "SAME");

        assertThatThrownBy(() -> createItem(typeA.getId(), "SAME"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("取值已存在");

        // 停用该项后不再出现在按编码取项的结果里
        DictItem disable = new DictItem();
        disable.setId(itemA.getId());
        // 更新走整体覆盖，标签与取值需一并带上（实现层没有做局部更新）
        disable.setLabel(itemA.getLabel());
        disable.setValue(itemA.getValue());
        disable.setStatus(EnableStatus.DISABLED);
        dictService.updateItem(disable);

        assertThat(dictService.listItemsByTypeCode(typeA.getCode()))
                .extracting(DictItem::getValue).doesNotContain("SAME");
    }

    @Test
    @DisplayName("字典项必填校验：缺类型、类型不存在、缺文本、缺取值均拒绝")
    void createItemValidatesRequiredFields() {
        DictType type = createType(unique("it_dict_req"));

        assertThatThrownBy(() -> createItem(null, "V1")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> createItem(type.getId() + 999_999L, "V2")).isInstanceOf(BizException.class);

        DictItem noLabel = itemOf(type.getId(), "V3");
        noLabel.setLabel(" ");
        assertThatThrownBy(() -> dictService.createItem(noLabel)).isInstanceOf(BizException.class);

        DictItem noValue = itemOf(type.getId(), "V4");
        noValue.setValue(null);
        assertThatThrownBy(() -> dictService.createItem(noValue)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("更新字典项不允许改变所属类型，避免原类型取值含义被悄悄改写")
    void updateItemCannotMoveToAnotherType() {
        DictType typeA = createType(unique("it_dict_ma"));
        DictType typeB = createType(unique("it_dict_mb"));
        DictItem item = dictService.createItem(itemOf(typeA.getId(), "MOVE"));

        DictItem patch = new DictItem();
        patch.setId(item.getId());
        patch.setTypeId(typeB.getId());
        patch.setLabel("改名了");
        patch.setValue("MOVE");
        DictItem updated = dictService.updateItem(patch);

        assertThat(updated.getTypeId()).isEqualTo(typeA.getId());
        assertThat(updated.getLabel()).isEqualTo("改名了");
    }

    @Test
    @DisplayName("删除类型连带删除其字典项，不留无法从界面进入的孤儿项")
    void deleteTypeCascadesItems() {
        DictType type = createType(unique("it_dict_del"));
        DictItem item = dictService.createItem(itemOf(type.getId(), "DEL"));

        dictService.deleteType(type.getId());

        assertThatThrownBy(() -> dictService.getTypeById(type.getId()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
        assertThatThrownBy(() -> dictService.getItemById(item.getId()))
                .isInstanceOf(BizException.class);
        // 类型没了，按编码取项也应为空
        assertThat(dictService.listItemsByTypeCode(type.getCode())).isEmpty();
    }

    @Test
    @DisplayName("类型分页：关键字命中编码或名称，且只返回命中项")
    void pageTypesFiltersByKeyword() {
        String marker = unique("it_dict_kw");
        createType(marker);

        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(1);
        pageQuery.setSize(10);
        DictQuery condition = new DictQuery();
        condition.setKeyword(marker);

        PageResult<DictType> page = dictService.pageTypes(pageQuery, condition);
        assertThat(page.getList()).extracting(DictType::getCode).containsExactly(marker);
        assertThat(page.getTotal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("字典项分页：typeId 过滤生效，关键字命中文本或取值")
    void pageItemsFiltersByTypeAndKeyword() {
        DictType type = createType(unique("it_dict_pi"));
        DictItem item = dictService.createItem(itemOf(type.getId(), "PAGED"));

        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(1);
        pageQuery.setSize(10);
        DictQuery condition = new DictQuery();
        condition.setTypeId(type.getId());
        condition.setKeyword("PAGED");

        PageResult<DictItem> page = dictService.pageItems(pageQuery, condition);
        assertThat(page.getList()).extracting(DictItem::getId).containsExactly(item.getId());

        // 换个不属于该类型的 typeId 应查不到
        DictQuery otherType = new DictQuery();
        otherType.setTypeId(type.getId() + 999_999L);
        assertThat(dictService.pageItems(pageQuery, otherType).getList()).isEmpty();
    }

    @Test
    @DisplayName("按编码取项的边界：空编码与不存在的编码返回空列表，不抛异常")
    void listItemsByTypeCodeHandlesUnknownCodes() {
        assertThat(dictService.listItemsByTypeCode("  ")).isEmpty();
        assertThat(dictService.listItemsByTypeCode(unique("it_dict_none"))).isEmpty();
    }

    @Test
    @DisplayName("字典项删除：删除后不再出现在按编码取项的结果里")
    void deleteItemRemovesFromPublicList() {
        DictType type = createType(unique("it_dict_di"));
        DictItem item = dictService.createItem(itemOf(type.getId(), "GONE"));

        assertThat(dictService.listItemsByTypeCode(type.getCode()))
                .extracting(DictItem::getValue).contains("GONE");

        dictService.deleteItem(item.getId());

        assertThatThrownBy(() -> dictService.getItemById(item.getId())).isInstanceOf(BizException.class);
        assertThat(dictService.listItemsByTypeCode(type.getCode())).isEmpty();
    }
}
