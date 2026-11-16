package com.xxhy.simpleai.data.db;

/**
 * 数据库表与列名常量。
 */
public final class Contract {

    private Contract() {
    }

    public static final String DB_NAME = "SimpleAI.db";
    public static final int DB_VERSION = 4;

    /** 会话表 */
    public static final class Sessions {
        public static final String TABLE = "sessions";
        public static final String ID = "id";
        public static final String TITLE = "title";
        public static final String API_CONFIG_ID = "api_config_id";
        public static final String MODEL = "model";
        public static final String CREATED_AT = "created_at";
        public static final String UPDATED_AT = "updated_at";
    }

    /** 消息表 */
    public static final class Messages {
        public static final String TABLE = "messages";
        public static final String ID = "id";
        public static final String SESSION_ID = "session_id";
        public static final String ROLE = "role";
        public static final String CONTENT = "content";
        public static final String CREATED_AT = "created_at";
        public static final String REASONING = "reasoning";
        public static final String MODEL = "model";
        public static final String USAGE_JSON = "usage_json";
        public static final String FINISH_REASON = "finish_reason";
        /** 附件 JSON（图片/文件 URI 列表） */
        public static final String ATTACHMENTS = "attachments";
    }

    /** API 配置表 */
    public static final class ApiConfigs {
        public static final String TABLE = "api_configs";
        public static final String ID = "id";
        public static final String NAME = "name";
        public static final String BASE_URL = "base_url";
        public static final String API_PATH = "api_path";
        public static final String API_KEY = "api_key";
        public static final String MODELS = "models";
        public static final String TEMPERATURE = "temperature";
        public static final String MAX_TOKENS = "max_tokens";
        public static final String CONTEXT_LENGTH = "context_length";
        public static final String IS_ACTIVE = "is_active";
        public static final String CREATED_AT = "created_at";
        public static final String PROVIDER_TYPE = "provider_type";
        public static final String ENABLED = "enabled";
        public static final String VISION_SUPPORTED = "vision_supported";
        public static final String REASONING_SUPPORTED = "reasoning_supported";
        public static final String INCLUDE_HISTORY_REASONING = "include_history_reasoning";
        public static final String SEARCH_ENABLED = "search_enabled";
        public static final String USE_RESPONSE_API = "use_response_api";
        public static final String BALANCE_ENABLED = "balance_enabled";
        public static final String BALANCE_API_PATH = "balance_api_path";
        public static final String BALANCE_RESULT_PATH = "balance_result_path";
    }

    /** 模型配置表（每条记录对应一个模型，关联到 API 配置） */
    public static final class Models {
        public static final String TABLE = "models";
        public static final String ID = "id";
        public static final String API_CONFIG_ID = "api_config_id";
        public static final String MODEL_ID = "model_id";
        public static final String DISPLAY_NAME = "display_name";
        public static final String TYPE = "type";
        public static final String INPUT_MODALITIES = "input_modalities";
        public static final String OUTPUT_MODALITIES = "output_modalities";
        public static final String ABILITIES = "abilities";
        public static final String CUSTOM_HEADERS = "custom_headers";
        public static final String CUSTOM_BODIES = "custom_bodies";
        public static final String PROVIDER_OVERWRITE = "provider_overwrite";
        public static final String CREATED_AT = "created_at";
    }
}
