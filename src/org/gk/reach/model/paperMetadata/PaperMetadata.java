package org.gk.reach.model.paperMetadata;

public class PaperMetadata {
    private Header header;
    private Result result;

    public void setHeader(Header header) {
        this.header = header;
    }
    public Header getHeader() {
        return header;
    }
    public void setResult(Result result) {
        this.result = result;
    }
    public Result getResult() {
        return result;
    }
}
