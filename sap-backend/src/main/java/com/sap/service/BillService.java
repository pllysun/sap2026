package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.common.BusinessException;
import com.sap.entity.Bill;
import com.sap.entity.BillImage;
import com.sap.mapper.BillImageMapper;
import com.sap.mapper.BillMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.sap.entity.Setting;
import com.sap.mapper.SettingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BillService {

    @Autowired
    private BillMapper billMapper;
    @Autowired
    private BillImageMapper billImageMapper;
    @Autowired
    private SettingMapper settingMapper;

    public Page<Map<String, Object>> listByGrade(String grade, int current, int size) {
        Page<Bill> page = new Page<>(current, size);
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        if (grade != null && !grade.isEmpty()) {
            wrapper.eq(Bill::getGrade, grade);
        }
        wrapper.orderByDesc(Bill::getBillTime);
        Page<Bill> billPage = billMapper.selectPage(page, wrapper);

        Page<Map<String, Object>> result = new Page<>(current, size, billPage.getTotal());
        result.setRecords(billPage.getRecords().stream().map(b -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", b.getId());
            map.put("billType", b.getBillType());
            map.put("content", b.getContent());
            map.put("amount", b.getAmount());
            map.put("billTime", b.getBillTime());
            map.put("remark", b.getRemark());
            map.put("grade", b.getGrade());
            List<BillImage> images = billImageMapper.selectList(
                    new LambdaQueryWrapper<BillImage>().eq(BillImage::getBillId, b.getId())
            );
            map.put("images", images);
            return map;
        }).collect(Collectors.toList()));
        return result;
    }

    @Transactional
    public void addBill(Bill bill, List<String> imageUrls) {
        // 从设置中读取当前年级
        if (bill.getGrade() == null || bill.getGrade().isEmpty()) {
            Setting gradeSetting = settingMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Setting>()
                            .eq(Setting::getSettingKey, "current_grade")
            );
            bill.setGrade(gradeSetting != null ? gradeSetting.getSettingValue() : "2025");
        }
        billMapper.insert(bill);
        if (imageUrls != null) {
            for (String url : imageUrls) {
                BillImage img = new BillImage();
                img.setBillId(bill.getId());
                img.setImageUrl(url);
                billImageMapper.insert(img);
            }
        }
    }

    @Transactional
    public void updateBill(Long id, Bill bill, List<String> imageUrls) {
        Bill existing = billMapper.selectById(id);
        if (existing == null) throw new BusinessException("账单不存在");
        existing.setBillType(bill.getBillType());
        existing.setContent(bill.getContent());
        existing.setAmount(bill.getAmount());
        existing.setBillTime(bill.getBillTime());
        existing.setRemark(bill.getRemark());
        existing.setGrade(bill.getGrade());
        billMapper.updateById(existing);

        if (imageUrls != null) {
            billImageMapper.delete(
                    new LambdaQueryWrapper<BillImage>().eq(BillImage::getBillId, id)
            );
            for (String url : imageUrls) {
                BillImage img = new BillImage();
                img.setBillId(id);
                img.setImageUrl(url);
                billImageMapper.insert(img);
            }
        }
    }

    public void deleteBill(Long id) {
        billMapper.deleteById(id);
    }

    /**
     * 统计：按年级统计收支
     */
    public Map<String, Object> getStats(String grade) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        if (grade != null && !grade.isEmpty()) {
            wrapper.eq(Bill::getGrade, grade);
        }
        List<Bill> bills = billMapper.selectList(wrapper);

        BigDecimal totalIncome = bills.stream()
                .filter(b -> b.getBillType() == 1)
                .map(Bill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = bills.stream()
                .filter(b -> b.getBillType() == 0)
                .map(Bill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalIncome", totalIncome);
        stats.put("totalExpense", totalExpense);
        stats.put("balance", totalIncome.subtract(totalExpense));
        stats.put("count", bills.size());
        return stats;
    }

    /**
     * 导出Excel
     */
    public byte[] exportExcel(String grade) throws Exception {
        List<Bill> bills = billMapper.selectList(
                new LambdaQueryWrapper<Bill>()
                        .eq(grade != null, Bill::getGrade, grade)
                        .orderByDesc(Bill::getBillTime)
        );

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("财务记录");
        Row header = sheet.createRow(0);
        String[] headers = {"类型", "内容", "金额", "时间", "备注", "年级"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < bills.size(); i++) {
            Bill b = bills.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(b.getBillType() == 0 ? "支出" : "收入");
            row.createCell(1).setCellValue(b.getContent());
            row.createCell(2).setCellValue(b.getAmount().doubleValue());
            row.createCell(3).setCellValue(b.getBillTime() != null ? b.getBillTime().format(fmt) : "");
            row.createCell(4).setCellValue(b.getRemark() != null ? b.getRemark() : "");
            row.createCell(5).setCellValue(b.getGrade() != null ? b.getGrade() : "");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }
}
