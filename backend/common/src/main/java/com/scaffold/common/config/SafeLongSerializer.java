package com.scaffold.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Long 序列化器：超出 JS 安全整数范围（±2^53-1）时写字符串，其余写数字。
 *
 * <p>雪花主键为 19 位整数，超过 JS Number 的精确表示上限，若按数字下发会在浏览器被
 * 四舍五入（例如 104879054015991814 与 ...813 都变成 ...800），表现为不同记录 ID 相同。
 *
 * <p>只对必然丢精度的值转字符串，可避免把 {@code code}、{@code total}、{@code parentId=0}
 * 这类小整数一并改成字符串，从而不影响前端既有的数值比较、分页计算与根节点判定。
 */
public class SafeLongSerializer extends JsonSerializer<Long> {

    /** JS Number 可精确表示的最大整数：2^53 - 1 */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER) {
            gen.writeString(value.toString());
        } else {
            gen.writeNumber(value);
        }
    }
}
