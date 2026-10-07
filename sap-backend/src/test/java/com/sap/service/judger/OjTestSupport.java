package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.entity.User;
import com.sap.mapper.UserMapper;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OjTestSupport {
    static UserMapper activeUsers() {
        UserMapper users=mock(UserMapper.class);
        when(users.selectById(anyLong())).thenAnswer(i->{User u=new User();u.setId(i.getArgument(0));u.setStatus(1);return u;});return users;
    }
    static OjSnapshotService snapshots() {
        OjSnapshotService service=mock(OjSnapshotService.class);
        when(service.compact(any())).thenAnswer(i->{var node=new ObjectMapper().valueToTree(i.getArgument(0));
            var object=(com.fasterxml.jackson.databind.node.ObjectNode)node;object.remove(java.util.List.of("pack","languages"));object.put("packRef","a".repeat(64));return object.toString();});
        return service;
    }
}
