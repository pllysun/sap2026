package com.sap.jw.parser;

import com.sap.jw.vo.GradeVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 强智成绩(cjcx_list, table#dataList)解析器。
 * <p>该表无内联表头，按固定列序解析：
 * 序号/开课学期/课程编号/课程名称/成绩/学分/总学时/绩点/成绩标志/考核方式/考试性质/课程属性/课程性质。</p>
 */
@Component
public class GradeParser {

    public List<GradeVO> parse(String html) {
        List<GradeVO> list = new ArrayList<>();
        for (List<String> row : JwTable.rows(html, "dataList")) {
            if (row.size() < 13) continue;
            String courseName = JwTable.at(row, 3);
            if (courseName.isBlank()) continue;
            GradeVO g = new GradeVO();
            g.setTerm(JwTable.at(row, 1));
            g.setCourseNo(JwTable.at(row, 2));
            g.setCourseName(courseName);
            g.setScore(JwTable.at(row, 4));
            g.setCredit(JwTable.at(row, 5));
            g.setHours(JwTable.at(row, 6));
            g.setGradePoint(JwTable.at(row, 7));
            g.setFlag(JwTable.at(row, 8));
            g.setAssessMethod(JwTable.at(row, 9));
            g.setExamNature(JwTable.at(row, 10));
            g.setCourseAttr(JwTable.at(row, 11));
            g.setCourseNature(JwTable.at(row, 12));
            list.add(g);
        }
        return list;
    }
}
