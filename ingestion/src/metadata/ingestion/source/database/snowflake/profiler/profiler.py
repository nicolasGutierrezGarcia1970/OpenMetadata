#  Copyright 2021 Collate
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#  http://www.apache.org/licenses/LICENSE-2.0
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

"""
Profiler for Snowflake
"""
from metadata.ingestion.source.database.snowflake.profiler.system import (
    SnowflakeSystemMetricsSource,
)
from metadata.profiler.interface.sqlalchemy.snowflake.profiler_interface import (
    SnowflakeProfilerInterface,
)


class SnowflakeProfiler(SnowflakeProfilerInterface):
    def initialize_system_metrics_computer(
        self, **kwargs
    ) -> SnowflakeSystemMetricsSource:
        return SnowflakeSystemMetricsSource(session=self.session)
