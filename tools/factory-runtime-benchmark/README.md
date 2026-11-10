# 工厂解释器分配微基准

`FactoryPerf.java` 只依赖 JDK 与共享工厂代码，构造 32 层调用中的物流循环：每 tick 执行 100 对 GET／PUT，加一次跳转与等待。Host 不执行世界访问。预热 2000 tick 后，用 `ThreadMXBean` 测量 5000 tick 的本线程分配字节与耗时。

`benchmark.py` 从给定 Git 提交提取基线共享源码，与当前工作树分别编译、在独立 JVM 中运行，并保存源码、类文件与 `result.json`。输出目录必须不存在，避免覆盖历史证据。

```powershell
python tools/factory-runtime-benchmark/benchmark.py --baseline 2998331a609abba7e255ee83be8c2ab03e34d164 --java-home C:/Users/12252/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2 --output .codex-tmp/factory-allocation-new
```

这是解释器热循环的分配微基准，不能据此声称整服 TPS、世界物流吞吐或冷启动内存改善相同比例。耗时受 JIT、系统负载和并行客户端影响；正确性另由共享单测及两代原生工厂场景验证。
