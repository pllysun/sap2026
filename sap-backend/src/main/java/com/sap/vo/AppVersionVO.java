package com.sap.vo;

import lombok.Data;

/** {@code GET /api/app/latest} 返回的版本元数据（仅几百字节，下载地址需会员登录才下发）。 */
@Data
public class AppVersionVO {
    private int versionCode;
    private String versionName;
    private String changelog;
    private boolean forceUpdate;
    private int minSupportedVersionCode;
    private String sha256;
    private long size;
    private String downloadUrl;
}
