package com.patchme.dict;

import com.patchme.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 固定字典出口（学校/专业/标签）：前端筛选选项唯一来源，用户不可自创。
 * 字典本身即公开数据，无敏感字段。
 */
@RestController
@RequestMapping("/api/dicts")
public class DictController {

    private final SchoolMapper schoolMapper;
    private final MajorMapper majorMapper;
    private final TagMapper tagMapper;

    public DictController(SchoolMapper schoolMapper, MajorMapper majorMapper, TagMapper tagMapper) {
        this.schoolMapper = schoolMapper;
        this.majorMapper = majorMapper;
        this.tagMapper = tagMapper;
    }

    @GetMapping
    public ApiResponse<DictVO> all() {
        return ApiResponse.success(new DictVO(
                items(schoolMapper.selectList(activeFirst())),
                items(majorMapper.selectList(activeFirst())),
                items(tagMapper.selectList(activeFirst()))));
    }

    private static <T extends DictEntity> List<DictItemVO> items(List<T> list) {
        return list.stream().map(d -> new DictItemVO(d.getId(), d.getName())).toList();
    }

    // 泛型方法无法构造实体的 lambda 列引用（MP 需要具体类），字典查询用字符串列名即可
    private static <T extends DictEntity> com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<T> activeFirst() {
        return new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<T>()
                .eq("active", 1).orderByAsc("sort_order");
    }

    public record DictVO(List<DictItemVO> schools, List<DictItemVO> majors, List<DictItemVO> tags) {
    }

    public record DictItemVO(Long id, String name) {
    }
}
