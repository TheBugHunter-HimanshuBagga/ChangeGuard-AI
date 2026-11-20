"""Application settings loaded from environment (see .env.example)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration for the intelligence plane."""

    model_config = SettingsConfigDict(
        env_prefix="ML_ENGINE_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    host: str = "0.0.0.0"
    port: int = 8000
    log_level: str = "INFO"
    service_name: str = "changeguard-ml-engine"
    service_version: str = "0.1.0"
    api_version: str = "v1"


@lru_cache
def get_settings() -> Settings:
    return Settings()


def reset_settings_cache() -> None:
    """Test helper: clear the cached settings."""
    get_settings.cache_clear()
