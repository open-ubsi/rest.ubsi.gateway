
--[[
eval "......" key_number KEY1 KEY2 cur_time KEY1_count KEY1_rate_time KEY2_count KEY2_rate_time
注意：cur_time、key?_rate_time都是long型的毫秒数
]]--

-- 第一个参数是当前时间
local cur_time = tonumber(ARGV[1])

for i = 1,#(KEYS) do
    local bucket = redis.call("HMGET", KEYS[i], "count", "time")
    if ( bucket[1] and bucket[2] ) then
        local last_time = tonumber(bucket[2])
        local rate_time = tonumber(ARGV[i*2+1])
        if ( last_time + rate_time <= cur_time ) then
            redis.call("HMSET", KEYS[i], "count", ARGV[i*2], "time", cur_time)
        else
            local left_count = tonumber(bucket[1])
            if ( left_count <= 0 ) then
                return 0
            end
        end
    else
        redis.call("HMSET", KEYS[i], "count", ARGV[i*2], "time", cur_time)
    end
end

for i = 1,#(KEYS) do
    redis.call("HINCRBY", KEYS[i], "count", -1)
end
return 1
