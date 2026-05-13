from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    completion_api_url: str = Field(default="", validation_alias="COMPLETION_API_URL")
    completion_api_key: str = Field(default="", validation_alias="COMPLETION_API_KEY")
    completion_model_id: str = Field(default="", validation_alias="COMPLETION_MODEL_ID")
    # Optional JSON object merged into POST headers (provider-specific; see vendor docs).
    completion_headers_json: str = Field(default="", validation_alias="COMPLETION_HEADERS_JSON")


@lru_cache
def get_settings() -> Settings:
    return Settings()
