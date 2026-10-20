package io.sapoj.service.judger;
import io.sapoj.common.BusinessException;
public class NodeUnavailableException extends BusinessException {
    public NodeUnavailableException() { super(503,"判题节点连接中断，正在尝试其他节点"); }
}
