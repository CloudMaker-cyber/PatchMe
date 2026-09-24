package com.patchme.dict;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 字典出口切片：三组字典齐出，仅 id/name 两字段。 */
@WebMvcTest(DictController.class)
@AutoConfigureMockMvc(addFilters = false)
class DictControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SchoolMapper schoolMapper;

    @MockitoBean
    private MajorMapper majorMapper;

    @MockitoBean
    private TagMapper tagMapper;

    private static SchoolEntity school(long id, String name) {
        SchoolEntity s = new SchoolEntity();
        s.setId(id);
        s.setName(name);
        s.setActive(true);
        return s;
    }

    @Test
    void returnsThreeDictGroups() throws Exception {
        when(schoolMapper.selectList(any())).thenReturn(List.of(school(1L, "北京大学")));
        when(majorMapper.selectList(any())).thenReturn(List.of());
        when(tagMapper.selectList(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/dicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schools[0].id").value(1))
                .andExpect(jsonPath("$.data.schools[0].name").value("北京大学"))
                .andExpect(jsonPath("$.data.majors").isArray())
                .andExpect(jsonPath("$.data.tags").isArray())
                .andExpect(jsonPath("$.data.schools[0].active").doesNotExist());
    }
}
