package com.lan4jn.file.dto;

public class StandardCertificateIssueRequest {

    private String requestId;
    private String csrPem;
    private String deviceLabel;

    public StandardCertificateIssueRequest() {
    }

    public StandardCertificateIssueRequest(String requestId, String csrPem, String deviceLabel) {
        this.requestId = requestId;
        this.csrPem = csrPem;
        this.deviceLabel = deviceLabel;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getCsrPem() {
        return csrPem;
    }

    public void setCsrPem(String csrPem) {
        this.csrPem = csrPem;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }
}
