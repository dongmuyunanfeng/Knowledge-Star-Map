package com.knowledgestarmap.common;

import java.util.List;

public class PageResult<T> {
    private long total;
    private int pages;
    private int page;
    private int pageSize;
    private List<T> list;

    public PageResult() {}

    public static <T> PageResult<T> of(long total, int page, int pageSize, List<T> list) {
        PageResult<T> r = new PageResult<>();
        r.total = total;
        r.page = page;
        r.pageSize = pageSize;
        r.pages = (int) Math.ceil((double) total / pageSize);
        r.list = list;
        return r;
    }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPages() { return pages; }
    public void setPages(int pages) { this.pages = pages; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }
}
