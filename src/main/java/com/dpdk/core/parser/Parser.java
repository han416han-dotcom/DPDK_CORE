package com.dpdk.core.parser;

/**
 * 解析器接口 — 所有解析器实现此接口。
 *
 * 扩展方式:
 * 1. 实现本接口
 * 2. 声明 @Component
 * 3. 自动被 Pipeline 收集并注册
 */
public interface Parser {

    /**
     * @return 所属解析阶段
     */
    ParseStage getStage();

    /**
     * 执行解析。读取 context 中前置阶段写入的数据，
     * 将本阶段结果写回 context。
     *
     * @param context 解析上下文
     * @return true=继续执行管道; false=中断管道
     */
    boolean parse(ParseContext context);

    /**
     * @return 解析器名称 (用于日志标识)
     */
    String getName();

    /**
     * @return 解析器版本号
     */
    default String getVersion() {
        return "1.0";
    }
}
