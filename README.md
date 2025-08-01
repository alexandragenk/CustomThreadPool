# CustomThreadPool

## Описание проекта

Кастомный пул потоков с настраиваемым управлением очередями, логированием, параметрами и политикой отказа.

## Основные функции

* **Least Loaded** распределение задач по нескольким очередям.
* **Настраиваемые параметры пула**: `corePoolSize`, `maxPoolSize`, `queueSize`, `keepAliveTime`, `minSpareThreads`.
* **Интерфейс** `CustomExecutor` (`execute`, `submit`, `shutdown`, `shutdownNow`).
* **Обработка отказов**: `RejectedException` при переполнении очереди.
* **Логирование** ключевых событий
* **Graceful shutdown**: мягкое завершение воркеров + await termination с принудительным завершением
* **Демонстрационная программа**: в Main файле


## Структура проекта

```
├── pom.xml
├── README.md
├── src
│   ├── main
│   │   ├── java
│   │   │   ├── CustomExecutor.java
│   │   │   ├── CustomThreadFactory.java
│   │   │   ├── CustomThreadPool.java
│   │   │   ├── Main.java
│   │   │   ├── RejectedException.java
│   │   │   └── Worker.java
│   │   └── resources
│   │       └── log4j2.xml

```

## Тестирование

Запуск `Main.java` демонстрирует базовую работу кастомного пула  и стандартного в сравнении, с разными типами задач и сценариями, по умолчанию с параметрами:

```
corePoolSize = 16
maxPoolSize  = 32
queueSize    = 100
keepAliveTime = 3 (c)
minSpareThreads = 1
```

В консоли выводятся логи о приёме и выполнении задач, а также итоговая статистика по каждому сценарию нагрузки для кастомного пула и стандартного FixedThreadPool

## Отчёт

### Анализ производительности

Для проведения анализа производительности использовались следующие сценарии:

1. **Умеренная нагрузка** 10 задач, по 10 ms
2. **Всплеск задач** 2000 задач, по 0 ms
3. **Высокая нагрузка** 800 задач, по 200 ms
4. **Долгие задачи** 6 задач, по 5000ms
6. **Смешанные задачи** 10 задач, по 500  и 4000 ms

#### Результаты 
| Сценарий              | Пул              | Выполнено | Отклонено | Общее время (мс) | Среднее время на задачу (мс) |
|-----------------------| ---------------- | --------- | --------- | ---------------- |------------------------------|
| 1. Умеренная нагрузка | CustomThreadPool | 10        | 0         | 22               | 10.00                        |
|                       | FixedThreadPool  | 10        | 0         | 18               | 13.00                        |
| 2. Всплеск задач      | CustomThreadPool | 2000      | 0         | 9                | 0.00                         |
|                       | FixedThreadPool  | 2000      | 0         | 4                | 0.00                         |
| 3. Высокая нагрузка   | CustomThreadPool | 800       | 0         | 8007             | 200.00                       |
|                       | FixedThreadPool  | 800       | 0         | 10007            | 200.00                       |
| 4. Долгие задачи      | CustomThreadPool | 6         | 0         | 5000             | 5000.00                      |
|                       | FixedThreadPool  | 6         | 0         | 5000             | 5000.00                      |
| 5. Смешанные задачи   | CustomThreadPool | 10        | 0         | 4507             | 0.00                         |
|                       | FixedThreadPool  | 10        | 0         | 4001             | 0.00                         |

Во всех сценариях нет отклонённых задач — нагрузка укладывается в ресурсы.

Разница между пулами наблюдается в:

Сценарии 1: CustomThreadPool быстрее по времени выполнения, но у FixedThreadPool выше средняя длительность.

Сценарии 3 и 4: идентичные показатели — одинаковая нагрузка, всё упирается в sleep.

Смешанные задачи: время похоже, но Custom чуть дольше обрабатывал.

### Подбор оптимальных параметров

#### `corePoolSize`

Сценарий 2. Всплеск задач

| corePoolSize | Выполнено | Отклонено | Общее время (мс) | Среднее время на задачу (мс) |
|--------------|-----------|-----------|------------------|------------------------------|
| 4            | 2000      | 0         | 14               | 0.00                         |
| 16           | 2000      | 0         | 16               | 0.00                         |
| 32           | 2000      | 0         | 11               | 0.00                         |
| 132          | 2000      | 0         | 17               | 0.00                         |

Сценарий 3. Высокая нагрузка

| corePoolSize | Выполнено | Отклонено | Общее время (мс) | Среднее время на задачу (мс) |
|--------------|-----------|-----------|------------------|------------------------------|
| 4            | 800       | 0         | 10017            | 200.00                       |
| 16           | 800       | 0         | 9010             | 200.00                       |
| 32           | 800       | 0         | 4805             | 200.00                       |
| 132          | 800       | 0         | 1202             | 200.00                       |

**Вывод:** при резком наплыве задач большее количество потоков снижает общее время реакции системы, даже если сами задачи очень короткие, но если потоков слишком много происходит обратная реакция.
При более длительных задачах и высоком объёме нагрузки увеличение corePoolSize даёт ощутимый выигрыш по времени, так как больше задач обрабатываются параллельно.

#### `maxPoolSize` (при corePoolSize 16)

Сценарий 2. Всплеск задач

| maxPoolSize | Выполнено | Отклонено | Общее время (мс) | Среднее время на задачу (мс) |
|--------------|-----------|-----------|------------------|-------------|
| 16           | 2000      | 0         | 10               | 0.00        |
| 32           | 2000      | 0         | 10               | 0.00        |
| 132          | 2000      | 0         | 15               | 0.00        |

Сценарий 3. Высокая нагрузка

| maxPoolSize | Выполнено | Отклонено | Общее время (мс) | время на задачу (мс) |
|--------------|-----------|-----------|------------------|--------------|
| 16           | 800       | 0         | 9009             | 200.00       |
| 32           | 800       | 0         | 5019             | 200.00       |
| 132          | 800       | 0         | 1458             | 200.00       |

**Вывод:** общее время выполнения минимально при 16 и 32, но возросло при 132, что может указывать на накладные расходы от избыточного количества потоков (context switching).
Увеличение maxPoolSize не приносит пользы при коротких burst-задачах, и даже может слегка замедлить выполнение.
При высокой нагрузке и долгих задачах — чем больше maxPoolSize, тем лучше масштабируемость, до тех пор, пока не достигнут предел по ресурсам CPU/памяти.

#### `queueSize`  (при corePoolSize 16, maxPoolSize 32)

Сценарий 3. Высокая нагрузка

| queueSize | Выполнено | Отклонено | Общее время (мс) | Среднее время на задачу (мс) |
|-------------|-----------|-----------|------------------|------------------------------|
| 5           | 192       | 608       | 1215             | 200.02                       |
| 50          | 800       | 0         | 5015             | 200.00                       |
| 100         | 800       | 0         | 5017             | 200.00                       |

**Вывод:** при слишком маленькой очереди задачи теряются при высокой нагрузке, 50 оптимально для данного сценария, при 100 работает стабильно,
но пользы по времени нет, возможен рост задержек

### Принцип работы механизма распределения задач

1. **Least Loaded**: очереди задач выбираются по принципу наименьшей загруженности
2. **Автоматическое масштабирование**: если потоков < `maxPoolSize`, создаётся новый воркер.
3. **Завершение простаивающих**: воркер завершается после бездействия дольше `keepAliveTime`, если общее число > `corePoolSize + minSpareThreads` чтобы обеспечить наличие запасных потоков.
