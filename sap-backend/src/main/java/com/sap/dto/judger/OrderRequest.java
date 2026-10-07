package com.sap.dto.judger;
import lombok.Data;
import java.util.List;
@Data public class OrderRequest { private List<Long> expected; private List<Long> ids; }
