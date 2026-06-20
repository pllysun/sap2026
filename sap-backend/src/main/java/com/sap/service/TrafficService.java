package com.sap.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.common.BusinessException;
import com.sap.entity.CosTraffic;
import com.sap.entity.FileObject;
import com.sap.entity.User;
import com.sap.mapper.ApiRequestStatMapper;
import com.sap.mapper.CosTrafficMapper;
import com.sap.mapper.FileObjectMapper;
import com.sap.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 流量/请求埋点采集服务。
 * <p>所有方法内部全程 try-catch 吞异常——埋点失败绝不可影响正常业务。</p>
 * <p>口径为"大概统计"：COS 真实字节始终走 CDN，后端只按文件大小近似累计（上传记 file.getSize()，
 * 下载经重定向端点据登记表查大小），不代理字节流，契合小带宽服务器。</p>
 */
@Service
public class TrafficService {

    @Autowired
    private FileObjectMapper fileObjectMapper;
    @Autowired
    private CosTrafficMapper cosTrafficMapper;
    @Autowired
    private ApiRequestStatMapper apiRequestStatMapper;
    @Autowired
    private UserMapper userMapper;

    /** 单用户每日上传字节配额（默认 500MB），防 COS 盗刷。可用 file.upload.daily-quota-bytes 覆盖。 */
    @org.springframework.beans.factory.annotation.Value("${file.upload.daily-quota-bytes:524288000}")
    private long dailyQuotaBytes;

    /** 单用户每日上传次数配额（默认 200 次）。可用 file.upload.daily-quota-count 覆盖。 */
    @org.springframework.beans.factory.annotation.Value("${file.upload.daily-quota-count:200}")
    private int dailyQuotaCount;

    private static long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o == null) return 0L;
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0L; }
    }

    /**
     * 上传前配额校验：超过单用户当日上传字节/次数上限则抛 BusinessException 拒绝，防 COS 存储/带宽盗刷。
     * 配额表查询异常时放行（埋点表问题不应阻断正常业务）。
     */
    public void checkUploadQuota(long incomingSize) {
        Object[] cu = currentUser();
        Long uid = (Long) cu[0];
        if (uid == null || uid == 0L) return;
        long bytes;
        long cnt;
        try {
            java.util.Map<String, Object> used = cosTrafficMapper.userDailyUpload(LocalDate.now(), uid);
            bytes = used != null ? toLong(used.get("bytes")) : 0L;
            cnt = used != null ? toLong(used.get("cnt")) : 0L;
        } catch (Exception ignore) {
            return; // 配额查询失败不阻断
        }
        if (cnt + 1 > dailyQuotaCount) {
            throw new BusinessException("今日上传次数已达上限(" + dailyQuotaCount + ")，请明日再试");
        }
        if (bytes + Math.max(0, incomingSize) > dailyQuotaBytes) {
            throw new BusinessException("今日上传流量已达上限(" + (dailyQuotaBytes / 1024 / 1024) + "MB)，请明日再试");
        }
    }

    /** 解析当前登录用户为 [userId(Long, 匿名=0), userName(String)]。 */
    private Object[] currentUser() {
        Long uid = 0L;
        String uname = "匿名";
        try {
            if (StpUtil.isLogin()) {
                uid = StpUtil.getLoginIdAsLong();
                User u = userMapper.selectById(uid);
                if (u != null) {
                    uname = u.getName() != null ? u.getName() : u.getStudentId();
                }
            }
        } catch (Exception ignore) {}
        return new Object[]{uid, uname};
    }

    /** 上传计量：登记文件对象（按 url 去重）+ 当前用户 UPLOAD 累加。 */
    public void recordUpload(long size, String cosKey, String url, String fileName) {
        try {
            Object[] cu = currentUser();
            Long uid = (Long) cu[0];
            String uname = (String) cu[1];
            try {
                if (url != null && fileObjectMapper.selectByUrl(url) == null) {
                    FileObject fo = new FileObject();
                    fo.setCosKey(cosKey);
                    fo.setUrl(url);
                    fo.setFileName(fileName);
                    fo.setSizeBytes(size);
                    fo.setUploaderId(uid);
                    fo.setUploaderName(uname);
                    fo.setCreateTime(LocalDateTime.now());
                    fileObjectMapper.insert(fo);
                }
            } catch (Exception ignore) {
                // 并发同 url 撞唯一键：忽略，登记已存在即可
            }
            cosTrafficMapper.upsert(LocalDate.now(), uid, uname, "UPLOAD", size);
        } catch (Exception ignore) {}
    }

    /** 下载计量：当前用户 DOWNLOAD 累加（size 由重定向端点据登记表/HEAD 提供）。 */
    public void recordDownload(long size) {
        try {
            Object[] cu = currentUser();
            cosTrafficMapper.upsert(LocalDate.now(), (Long) cu[0], (String) cu[1], "DOWNLOAD", size);
        } catch (Exception ignore) {}
    }

    /** 按 url 查登记的文件大小，找不到返回 -1。 */
    public long sizeOf(String url) {
        try {
            FileObject fo = fileObjectMapper.selectByUrl(url);
            if (fo != null && fo.getSizeBytes() != null) return fo.getSizeBytes();
        } catch (Exception ignore) {}
        return -1;
    }

    /** 接口请求计数：当前用户对该接口 +1。 */
    public void recordApiRequest(String endpoint, String httpMethod) {
        try {
            Object[] cu = currentUser();
            apiRequestStatMapper.upsert(LocalDate.now(), (Long) cu[0], (String) cu[1], endpoint, httpMethod);
        } catch (Exception ignore) {}
    }
}
