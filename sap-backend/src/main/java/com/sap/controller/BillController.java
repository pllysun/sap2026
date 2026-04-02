package com.sap.controller;

import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.entity.Bill;
import com.sap.service.BillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bill")
public class BillController {

    @Autowired
    private BillService billService;

    @GetMapping("/list")
    @OperationLog("查询财务列表")
    public Result<?> list(@RequestParam(required = false) String grade,
                          @RequestParam(defaultValue = "1") int current,
                          @RequestParam(defaultValue = "10") int size) {
        return Result.ok(billService.listByGrade(grade, current, size));
    }

    @PostMapping
    @OperationLog("新增财务记录")
    public Result<?> add(@RequestBody Map<String, Object> params) {
        Bill bill = new Bill();
        bill.setBillType((Integer) params.get("billType"));
        bill.setContent((String) params.get("content"));
        bill.setAmount(new BigDecimal(params.get("amount").toString()));
        if (params.get("billTime") != null) {
            bill.setBillTime(LocalDateTime.parse((String) params.get("billTime"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        bill.setRemark((String) params.get("remark"));
        bill.setGrade((String) params.get("grade"));
        List<String> imageUrls = (List<String>) params.get("imageUrls");
        billService.addBill(bill, imageUrls);
        return Result.ok("添加成功");
    }

    @PutMapping("/{id}")
    @OperationLog("修改财务记录")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Bill bill = new Bill();
        bill.setBillType((Integer) params.get("billType"));
        bill.setContent((String) params.get("content"));
        bill.setAmount(new BigDecimal(params.get("amount").toString()));
        if (params.get("billTime") != null) {
            bill.setBillTime(LocalDateTime.parse((String) params.get("billTime"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        bill.setRemark((String) params.get("remark"));
        bill.setGrade((String) params.get("grade"));
        List<String> imageUrls = (List<String>) params.get("imageUrls");
        billService.updateBill(id, bill, imageUrls);
        return Result.ok("修改成功");
    }

    @DeleteMapping("/{id}")
    @OperationLog("删除财务记录")
    public Result<?> delete(@PathVariable Long id) {
        billService.deleteBill(id);
        return Result.ok("删除成功");
    }

    @GetMapping("/stats")
    @OperationLog("查询财务统计")
    public Result<?> stats(@RequestParam(required = false) String grade) {
        return Result.ok(billService.getStats(grade));
    }

    @GetMapping("/export")
    @OperationLog("导出财务报表")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String grade) throws Exception {
        byte[] data = billService.exportExcel(grade);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "finance_" + grade + ".xlsx");
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
