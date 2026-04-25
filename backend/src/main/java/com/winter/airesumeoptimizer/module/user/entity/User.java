package com.winter.airesumeoptimizer.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id; // 主键ID

    private String username; // 用户名，唯一

    private String email; // 邮箱，唯一

    private String passwordHash;// 密码哈希值

    private String nickname;// 昵称

    private LocalDateTime createdAt;// 创建时间

    private LocalDateTime updatedAt;// 更新时间
}
