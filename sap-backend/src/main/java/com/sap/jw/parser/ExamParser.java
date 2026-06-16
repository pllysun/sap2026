package com.sap.jw.parser;

import com.sap.jw.vo.ExamVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 强智考试安排(xsksap_list, table#dataList)解析器。
 * <p>按固定列序解析：序号/考试场次/课程编号/课程名称/考试时间/考场/座位号/准考证号/操作。</p>
 */
@Component
public class ExamParser {

    public List<ExamVO> parse(String html) {
        List<ExamVO> list = new ArrayList<>();
        for (List<String> row : JwTable.rows(html, "dataList")) {
            if (row.size() < 8) continue;
            String courseName = JwTable.at(row, 3);
            if (courseName.isBlank()) continue; // “未查询到数据”等
            ExamVO e = new ExamVO();
            e.setSession(JwTable.at(row, 1));
            e.setCourseNo(JwTable.at(row, 2));
            e.setCourseName(courseName);
            e.setTime(JwTable.at(row, 4));
            e.setRoom(JwTable.at(row, 5));
            e.setSeat(JwTable.at(row, 6));
            e.setAdmissionTicket(JwTable.at(row, 7));
            list.add(e);
        }
        return list;
    }
}
